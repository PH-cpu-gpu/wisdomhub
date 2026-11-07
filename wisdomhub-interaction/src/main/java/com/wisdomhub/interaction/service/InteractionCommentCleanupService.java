package com.wisdomhub.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.interaction.config.CommentCleanupProperties;
import com.wisdomhub.interaction.constants.CommentConstants;
import com.wisdomhub.interaction.domain.entity.Comment;
import com.wisdomhub.interaction.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 按保留期分批物理删除软删评论。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionCommentCleanupService {

    private final CommentMapper commentMapper;
    private final CommentCleanupProperties properties;

    public CommentCleanupResult cleanup() {
        validate();
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.getRetentionDays());
        int scanned = 0;
        int deleted = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Comment> rows = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .select(Comment::getId)
                    .eq(Comment::getDeleted, (byte) CommentConstants.DELETED)
                    .lt(Comment::getUpdateTime, cutoff)
                    .orderByAsc(Comment::getUpdateTime)
                    .orderByAsc(Comment::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            List<Long> ids = rows.stream().map(Comment::getId).toList();
            try {
                deleted += commentMapper.delete(new LambdaQueryWrapper<Comment>()
                        .in(Comment::getId, ids)
                        .eq(Comment::getDeleted, (byte) CommentConstants.DELETED));
            } catch (RuntimeException e) {
                log.error("清理互动评论墓碑失败, size={}", ids.size(), e);
                failed++;
                break;
            }
            if (rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        return new CommentCleanupResult(scanned, deleted, failed);
    }

    private boolean pause() {
        if (properties.getPauseMillis() <= 0) {
            return true;
        }
        try {
            Thread.sleep(properties.getPauseMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void validate() {
        if (properties.getRetentionDays() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.comment.retention-days 必须大于 0");
        }
        if (properties.getBatchSize() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.comment.batch-size 必须大于 0");
        }
        if (properties.getMaxBatchesPerRun() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.comment.max-batches-per-run 必须大于 0");
        }
    }
}
