package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ArticleRedisKeyConstants;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.service.ArticleViewCountSyncService;
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

/**
 * 把 Redis 的阅读量计数刷回 article.view_count 快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleViewCountSyncServiceImpl implements ArticleViewCountSyncService {

    private static final int BATCH_SIZE = 500;
    private static final String KEY_PREFIX = ArticleRedisKeyConstants.VIEW_COUNT_KEY;

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String syncViewCounts() {
        int scanned = 0;
        int written = 0;
        int skipped = 0;
        int failedBatches = 0;

        // 边扫边攒批：SCAN 游标只走一遍，不在内存里留全量 key
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
            failedBatches += result.failed() ? 1 : 0;
        }

        String summary = "扫描阅读量 key " + scanned + " 个，回写 article.view_count " + written
                + " 处，跳过 " + skipped + " 个（文章不存在 / 值非法 / 读不到），失败 " + failedBatches + " 批";
        if (failedBatches > 0) {
            // 写的是绝对值、重跑幂等；但要抛出去让调度中心把这次执行标红，而不是静默成功
            throw new IllegalStateException(summary);
        }
        return summary;
    }

    /** 一批：炸了就吞掉并标记，不连累其余批次；最后按失败批数把整任务标红 */
    private BatchResult flushBatch(List<String> keys) {
        try {
            return doFlushBatch(keys);
        } catch (RuntimeException e) {
            log.error("阅读量落库批失败, 跳过继续: size={}", keys.size(), e);
            return new BatchResult(0, 0, true);
        }
    }

    private BatchResult doFlushBatch(List<String> keys) {
        List<Long> articleIds = new ArrayList<>(keys.size());
        int skipped = 0;
        for (String key : keys) {
            try {
                articleIds.add(Long.valueOf(key.substring(KEY_PREFIX.length())));
            } catch (NumberFormatException e) {
                // 前缀下的 key 只由本服务写，出现非数字只会是人工误写
                log.warn("跳过非法的阅读量 key: {}", key);
                skipped++;
            }
        }
        if (articleIds.isEmpty()) {
            return new BatchResult(0, skipped, false);
        }

        // MGET 一次往返取全部值（本项目 Redis 是单节点，不涉及 Cluster 的 CROSSSLOT）
        List<String> validKeys = articleIds.stream().map(id -> KEY_PREFIX + id).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(validKeys);
        if (values == null || values.size() != articleIds.size()) {
            throw new IllegalStateException("MGET 返回条数与请求不一致");
        }

        Map<Long, Integer> redisCounts = new HashMap<>(articleIds.size());
        for (int i = 0; i < articleIds.size(); i++) {
            String raw = values.get(i);
            // SCAN 之后 key 消失：读不到就跳过，绝不能把 MySQL 的快照清零
            Integer count = raw == null ? null : parseCount(raw, validKeys.get(i));
            if (count == null) {
                skipped++;
                continue;
            }
            redisCounts.put(articleIds.get(i), count);
        }
        if (redisCounts.isEmpty()) {
            return new BatchResult(0, skipped, false);
        }

        // 先查出当前值再比对：只有真的变了才发 UPDATE，且顺带得到「文章是否存在」的判据
        Map<Long, Integer> dbCounts = new HashMap<>(redisCounts.size());
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getViewCount)
                .in(Article::getId, redisCounts.keySet())
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED));
        for (Article row : rows) {
            dbCounts.put(row.getId(), row.getViewCount() == null ? 0 : row.getViewCount());
        }

        int written = 0;
        for (Map.Entry<Long, Integer> entry : redisCounts.entrySet()) {
            Integer dbValue = dbCounts.get(entry.getKey());
            if (dbValue == null) {
                // 文章不存在/已逻辑删除 = 孤儿 key。
                skipped++;
                log.debug("阅读量孤儿 key, 文章不存在: articleId={}", entry.getKey());
                continue;
            }
            if (dbValue.equals(entry.getValue())) {
                continue;
            }
            // 更新浏览量不用更新最近更新时间
            written += articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .set(Article::getViewCount, entry.getValue())
                    .eq(Article::getId, entry.getKey())
                    .eq(Article::getDeleted, ArticleConstants.NOT_DELETED));
        }
        return new BatchResult(written, skipped, false);
    }

    /** 解析计数；非数字、负数、超 INT 上限都返回 null 让调用方跳过 */
    private Integer parseCount(String raw, String key) {
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                log.warn("跳过超范围的阅读量: key={}, value={}", key, raw);
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            log.warn("跳过非法的阅读量值: key={}, value={}", key, raw);
            return null;
        }
    }

    private record BatchResult(int written, int skipped, boolean failed) {
    }
}
