package com.wisdomhub.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.interaction.api.QuestionAnswerFeign;
import com.wisdomhub.interaction.constants.CommentConstants;
import com.wisdomhub.interaction.domain.entity.Comment;
import com.wisdomhub.interaction.domain.entity.ContentFavorite;
import com.wisdomhub.interaction.domain.entity.ContentLike;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import com.wisdomhub.interaction.mapper.CommentMapper;
import com.wisdomhub.interaction.mapper.ContentFavoriteMapper;
import com.wisdomhub.interaction.mapper.ContentLikeMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** target.deleted 的本地级联：软删评论、物理删点赞/收藏、删计数行。重复执行影响 0 行。 */
@Service
@RequiredArgsConstructor
public class TargetDeletedService {

    private static final int ANSWER_BATCH_SIZE = 500;

    private final CommentMapper commentMapper;
    private final ContentLikeMapper contentLikeMapper;
    private final ContentFavoriteMapper contentFavoriteMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final QuestionAnswerFeign questionAnswerFeign;

    @Transactional
    public void purge(int targetType, Long targetId) {
        if (targetType == ContentTargetTypeConstants.QUESTION) {
            // 删问题会连带软删回答；一次回查拿全量回答 ID，含已删除的
            List<Long> answerIds = questionAnswerFeign.listAnswerIds(targetId);
            purgeTargets(ContentTargetTypeConstants.QUESTION, List.of(targetId));
            if (answerIds != null && !answerIds.isEmpty()) {
                for (int i = 0; i < answerIds.size(); i += ANSWER_BATCH_SIZE) {
                    purgeTargets(ContentTargetTypeConstants.ANSWER,
                            answerIds.subList(i, Math.min(i + ANSWER_BATCH_SIZE, answerIds.size())));
                }
            }
            return;
        }
        purgeTargets(targetType, List.of(targetId));
    }

    private void purgeTargets(int targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .set(Comment::getDeleted, (byte) CommentConstants.DELETED)
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getTargetType, (byte) targetType)
                .in(Comment::getTargetId, targetIds)
                .eq(Comment::getDeleted, (byte) CommentConstants.NOT_DELETED));
        contentLikeMapper.delete(new LambdaQueryWrapper<ContentLike>()
                .eq(ContentLike::getTargetType, (byte) targetType)
                .in(ContentLike::getTargetId, targetIds));
        contentFavoriteMapper.delete(new LambdaQueryWrapper<ContentFavorite>()
                .eq(ContentFavorite::getTargetType, (byte) targetType)
                .in(ContentFavorite::getTargetId, targetIds));
        interactionCountMapper.delete(new LambdaQueryWrapper<InteractionCount>()
                .eq(InteractionCount::getTargetType, (byte) targetType)
                .in(InteractionCount::getTargetId, targetIds));
    }
}
