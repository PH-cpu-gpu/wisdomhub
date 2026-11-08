package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.constants.VideoRedisKeyConstants;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.mapper.VideoMapper;
import com.wisdomhub.video.service.VideoViewCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoViewCountSyncServiceImpl implements VideoViewCountSyncService {

    private static final int BATCH_SIZE = 500;
    private static final String KEY_PREFIX = VideoRedisKeyConstants.VIEW_COUNT_KEY;

    private final VideoMapper videoMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String syncViewCounts() {
        int scanned = 0;
        int written = 0;
        int skipped = 0;
        int orphanDeleted = 0;
        int failedBatches = 0;

        List<String> batch = new ArrayList<>(BATCH_SIZE);
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(BATCH_SIZE).build())) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= BATCH_SIZE) {
                    scanned += batch.size();
                    BatchResult result = flushBatch(batch);
                    written += result.written();
                    skipped += result.skipped();
                    orphanDeleted += result.orphanDeleted();
                    failedBatches += result.failed() ? 1 : 0;
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            scanned += batch.size();
            BatchResult result = flushBatch(batch);
            written += result.written();
            skipped += result.skipped();
            orphanDeleted += result.orphanDeleted();
            failedBatches += result.failed() ? 1 : 0;
        }

        String summary = "扫描播放量 key " + scanned + " 个，回写 video.view_count " + written
                + " 处，删除孤儿 key " + orphanDeleted + " 个，跳过 " + skipped
                + " 个，失败 " + failedBatches + " 批";
        if (failedBatches > 0) {
            throw new IllegalStateException(summary);
        }
        return summary;
    }

    private BatchResult flushBatch(List<String> keys) {
        try {
            return doFlushBatch(keys);
        } catch (RuntimeException e) {
            log.error("播放量落库批失败, 跳过继续: size={}", keys.size(), e);
            return new BatchResult(0, 0, 0, true);
        }
    }

    private BatchResult doFlushBatch(List<String> keys) {
        List<Long> videoIds = new ArrayList<>(keys.size());
        int skipped = 0;
        for (String key : keys) {
            try {
                videoIds.add(Long.valueOf(key.substring(KEY_PREFIX.length())));
            } catch (NumberFormatException e) {
                log.warn("跳过非法的播放量 key: {}", key);
                skipped++;
            }
        }
        if (videoIds.isEmpty()) {
            return new BatchResult(0, skipped, 0, false);
        }

        List<String> validKeys = videoIds.stream().map(id -> KEY_PREFIX + id).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(validKeys);
        if (values == null || values.size() != videoIds.size()) {
            throw new IllegalStateException("MGET 返回条数与请求不一致");
        }

        Map<Long, Integer> redisCounts = new HashMap<>(videoIds.size());
        for (int i = 0; i < videoIds.size(); i++) {
            String raw = values.get(i);
            Integer count = raw == null ? null : parseCount(raw, validKeys.get(i));
            if (count == null) {
                skipped++;
                continue;
            }
            redisCounts.put(videoIds.get(i), count);
        }
        if (redisCounts.isEmpty()) {
            return new BatchResult(0, skipped, 0, false);
        }

        Map<Long, Integer> dbCounts = new HashMap<>(redisCounts.size());
        List<Video> rows = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getViewCount)
                .in(Video::getId, redisCounts.keySet())
                .ne(Video::getStatus, VideoConstants.DELETED));
        for (Video row : rows) {
            dbCounts.put(row.getId(), row.getViewCount() == null ? 0 : row.getViewCount());
        }

        int written = 0;
        int orphanDeleted = 0;
        for (Map.Entry<Long, Integer> entry : redisCounts.entrySet()) {
            Integer dbValue = dbCounts.get(entry.getKey());
            if (dbValue == null) {
                String orphanKey = KEY_PREFIX + entry.getKey();
                try {
                    if (Boolean.TRUE.equals(stringRedisTemplate.delete(orphanKey))) {
                        orphanDeleted++;
                    }
                } catch (RuntimeException e) {
                    log.warn("删除播放量孤儿 key 失败: {}", orphanKey, e);
                    skipped++;
                }
                continue;
            }
            if (dbValue.equals(entry.getValue())) {
                continue;
            }
            written += videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                    .set(Video::getViewCount, entry.getValue())
                    .eq(Video::getId, entry.getKey())
                    .ne(Video::getStatus, VideoConstants.DELETED));
        }
        return new BatchResult(written, skipped, orphanDeleted, false);
    }

    private Integer parseCount(String raw, String key) {
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                log.warn("跳过超范围的播放量: key={}, value={}", key, raw);
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            log.warn("跳过非法的播放量值: key={}, value={}", key, raw);
            return null;
        }
    }

    private record BatchResult(int written, int skipped, int orphanDeleted, boolean failed) {
    }
}
