package com.wisdomhub.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.interaction.config.CountReconcileProperties;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import com.wisdomhub.interaction.mapper.InteractionCountReconcileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 以互动明细为准校正 interaction_count；按 targetType 分组，分批扫描。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionCountReconcileService {

    private static final int[] TARGET_TYPES = {
            ContentTargetTypeConstants.ARTICLE,
            ContentTargetTypeConstants.ANSWER,
            ContentTargetTypeConstants.QUESTION,
            ContentTargetTypeConstants.COLUMN,
            ContentTargetTypeConstants.USER,
            ContentTargetTypeConstants.TAG_PROPOSAL,
            ContentTargetTypeConstants.VIDEO
    };

    private final InteractionCountReconcileMapper reconcileMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final CountReconcileProperties properties;

    public CountReconcileResult reconcile() {
        CountReconcileResult result = new CountReconcileResult();
        for (int targetType : TARGET_TYPES) {
            reconcileType(targetType, result);
        }
        return result;
    }

    private void reconcileType(int targetType, CountReconcileResult result) {
        String typeName = ContentTargetTypeConstants.nameOf(targetType);
        List<InteractionCount> details;
        try {
            details = reconcileMapper.countDetails(targetType);
        } catch (RuntimeException e) {
            log.error("读取 {} 明细聚合失败", typeName, e);
            result.add(targetType, 0, 0, 0, 0, 0, 1);
            return;
        }
        if (details == null) {
            details = List.of();
        }
        Map<Long, InteractionCount> actual = new HashMap<>(details.size());
        for (InteractionCount detail : details) {
            actual.put(detail.getTargetId(), detail);
        }

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int inserted = 0;
        int deleted = 0;
        int failed = 0;
        boolean scanFailed = false;
        Set<Long> seen = new HashSet<>();
        long lastId = 0;

        while (true) {
            List<InteractionCount> batch;
            try {
                batch = interactionCountMapper.selectList(new LambdaQueryWrapper<InteractionCount>()
                        .eq(InteractionCount::getTargetType, (byte) targetType)
                        .gt(InteractionCount::getTargetId, lastId)
                        .orderByAsc(InteractionCount::getTargetId)
                        .last("LIMIT " + properties.getBatchSize()));
            } catch (RuntimeException e) {
                log.error("扫描 {} 计数快照失败, lastId={}", typeName, lastId, e);
                failed++;
                scanFailed = true;
                break;
            }
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            for (InteractionCount row : batch) {
                InteractionCount expected = actual.get(row.getTargetId());
                if (expected == null) {
                    try {
                        interactionCountMapper.deleteById(row.getId());
                        deleted++;
                    } catch (RuntimeException e) {
                        log.error("删除孤儿计数行失败, id={}", row.getId(), e);
                        failed++;
                    }
                    continue;
                }
                seen.add(row.getTargetId());
                boolean likeChanged = !Objects.equals(row.getLikeCount(), expected.getLikeCount());
                boolean commentChanged = !Objects.equals(row.getCommentCount(), expected.getCommentCount());
                boolean favoriteChanged = !Objects.equals(row.getFavoriteCount(), expected.getFavoriteCount());
                if (!likeChanged && !commentChanged && !favoriteChanged) {
                    continue;
                }
                LambdaUpdateWrapper<InteractionCount> update = new LambdaUpdateWrapper<InteractionCount>()
                        .eq(InteractionCount::getId, row.getId());
                int changedColumns = 0;
                if (likeChanged) {
                    update.set(InteractionCount::getLikeCount, expected.getLikeCount());
                    changedColumns++;
                }
                if (commentChanged) {
                    update.set(InteractionCount::getCommentCount, expected.getCommentCount());
                    changedColumns++;
                }
                if (favoriteChanged) {
                    update.set(InteractionCount::getFavoriteCount, expected.getFavoriteCount());
                    changedColumns++;
                }
                try {
                    if (interactionCountMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns += changedColumns;
                    }
                } catch (RuntimeException e) {
                    log.error("修正计数行失败, id={}", row.getId(), e);
                    failed++;
                }
            }
            lastId = batch.get(batch.size() - 1).getTargetId();
            if (batch.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }

        if (!scanFailed) {
            for (Map.Entry<Long, InteractionCount> entry : actual.entrySet()) {
                if (seen.contains(entry.getKey())) {
                    continue;
                }
                InteractionCount expected = entry.getValue();
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                InteractionCount row = InteractionCount.builder()
                        .id(IdWorker.getId())
                        .targetType((byte) targetType)
                        .targetId(entry.getKey())
                        .likeCount(expected.getLikeCount())
                        .commentCount(expected.getCommentCount())
                        .favoriteCount(expected.getFavoriteCount())
                        .createTime(now)
                        .updateTime(now)
                        .build();
                try {
                    interactionCountMapper.insert(row);
                    inserted++;
                } catch (DuplicateKeyException e) {
                    // 并发写入已经补上了这一行
                    log.debug("计数行已被并发插入, targetType={}, targetId={}", targetType, entry.getKey());
                } catch (RuntimeException e) {
                    log.error("补插计数行失败, targetType={}, targetId={}", targetType, entry.getKey(), e);
                    failed++;
                }
            }
        }

        result.add(targetType, scanned + inserted, fixedRows, fixedColumns, inserted, deleted, failed);
    }

    private boolean pause() {
        try {
            Thread.sleep(properties.getPauseMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
