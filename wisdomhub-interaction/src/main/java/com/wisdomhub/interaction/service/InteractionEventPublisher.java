package com.wisdomhub.interaction.service;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.mq.event.ArticleLikedEvent;
import com.wisdomhub.common.mq.event.AnswerLikedEvent;
import com.wisdomhub.common.mq.event.BehaviorRecordedEvent;
import com.wisdomhub.common.mq.event.CommentCreatedEvent;
import com.wisdomhub.common.mq.event.VideoLikedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleLikedPayload;
import com.wisdomhub.common.mq.event.payload.AnswerLikedPayload;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.common.mq.event.payload.CommentCreatedPayload;
import com.wisdomhub.common.mq.event.payload.VideoLikedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.interaction.api.ArticleTagFeign;
import com.wisdomhub.interaction.api.VideoTagFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** 互动事件发送。发送失败只记日志，不影响已经提交的写操作。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionEventPublisher {

    private final EventPublisher eventPublisher;
    private final ArticleTagFeign articleTagFeign;
    private final VideoTagFeign videoTagFeign;

    public void articleLiked(Long articleId, Long userId, Long authorId) {
        if (authorId == null) {
            log.warn("文章点赞事件缺少作者, articleId={}, userId={}", articleId, userId);
            return;
        }
        try {
            eventPublisher.send(new ArticleLikedEvent(ArticleLikedPayload.builder()
                    .articleId(articleId)
                    .userId(userId)
                    .authorId(authorId)
                    .build()));
        } catch (Exception e) {
            log.error("文章点赞事件发送失败, articleId={}, userId={}", articleId, userId, e);
        }
    }

    public void answerLiked(Long answerId, Long userId, Long authorId) {
        if (authorId == null) {
            log.warn("回答点赞事件缺少作者, answerId={}, userId={}", answerId, userId);
            return;
        }
        try {
            eventPublisher.send(new AnswerLikedEvent(AnswerLikedPayload.builder()
                    .answerId(answerId)
                    .userId(userId)
                    .authorId(authorId)
                    .build()));
        } catch (Exception e) {
            log.error("回答点赞事件发送失败, answerId={}, userId={}", answerId, userId, e);
        }
    }

    public void videoLiked(Long videoId, Long userId, Long authorId) {
        if (authorId == null) {
            log.warn("视频点赞事件缺少作者, videoId={}, userId={}", videoId, userId);
            return;
        }
        try {
            eventPublisher.send(new VideoLikedEvent(VideoLikedPayload.builder()
                    .videoId(videoId)
                    .userId(userId)
                    .authorId(authorId)
                    .build()));
        } catch (Exception e) {
            log.error("视频点赞事件发送失败, videoId={}, userId={}", videoId, userId, e);
        }
    }

    public void commentCreated(Long commentId, int targetType, Long targetId, Long userId, Long receiverId) {
        if (receiverId == null) {
            log.warn("评论事件缺少接收者, commentId={}, targetType={}, targetId={}",
                    commentId, targetType, targetId);
            return;
        }
        try {
            eventPublisher.send(new CommentCreatedEvent(CommentCreatedPayload.builder()
                    .commentId(commentId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .userId(userId)
                    .receiverId(receiverId)
                    .build()));
        } catch (Exception e) {
            log.error("评论发表事件发送失败, commentId={}, targetType={}, targetId={}",
                    commentId, targetType, targetId, e);
        }
    }

    /** 文章与视频行为；tagIds 从对应服务取，取不到就跳过，不拖累点赞/收藏。 */
    public void behaviorRecorded(int targetType, Long targetId, Long userId, String behaviorType) {
        if (targetType != ContentTargetTypeConstants.ARTICLE
                && targetType != ContentTargetTypeConstants.VIDEO) {
            return;
        }
        try {
            BehaviorRecordedPayload.BehaviorRecordedPayloadBuilder builder = BehaviorRecordedPayload.builder()
                    .userId(userId)
                    .behaviorType(behaviorType);
            if (targetType == ContentTargetTypeConstants.ARTICLE) {
                builder.articleId(targetId).tagIds(articleTagFeign.listTagIds(targetId));
            } else {
                builder.videoId(targetId).tagIds(videoTagFeign.listTagIds(targetId));
            }
            eventPublisher.send(new BehaviorRecordedEvent(builder.build()));
        } catch (Exception e) {
            log.error("行为事件发送失败, targetType={}, targetId={}, userId={}, type={}",
                    targetType, targetId, userId, behaviorType, e);
        }
    }
}
