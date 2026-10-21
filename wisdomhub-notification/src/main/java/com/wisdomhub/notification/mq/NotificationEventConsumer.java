package com.wisdomhub.notification.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.AgentAnalysisCompletedPayload;
import com.wisdomhub.common.mq.event.payload.AnswerCreatedPayload;
import com.wisdomhub.common.mq.event.payload.AnswerLikedPayload;
import com.wisdomhub.common.mq.event.payload.ArticleLikedPayload;
import com.wisdomhub.common.mq.event.payload.CommentCreatedPayload;
import com.wisdomhub.common.mq.event.payload.OrderPaidPayload;
import com.wisdomhub.common.mq.event.payload.UserFollowedPayload;
import com.wisdomhub.notification.config.NotificationMqConfiguration;
import com.wisdomhub.notification.constants.NotificationRedisConstants;
import com.wisdomhub.notification.constants.NotificationTargetTypeConstants;
import com.wisdomhub.notification.constants.NotificationTypeConstants;
import com.wisdomhub.notification.domain.entity.Notification;
import com.wisdomhub.notification.service.NotificationPushService;
import com.wisdomhub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 通知落库消费端。去重在这里做，不进 common-mq：通知没有业务主键可收敛，是唯一注定要 eventId 去重的地方。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;
    private final NotificationPushService notificationPushService;

    @RabbitListener(queues = NotificationMqConfiguration.NOTIFICATION_QUEUE)
    public void onEvent(NotificationEventEnvelope event, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(NotificationEventEnvelope event) {
        if (!handled(event.getEventType())) {
            log.debug("忽略不关心的路由键: {}", event.getEventType());
            return;
        }
        String dedupKey = NotificationRedisConstants.EVENT_DEDUP_KEY + event.getEventId();
        // Redis 不可用时直接抛出：这里没有业务主键兜底，宁可进重试/死信也不产生重复通知
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", NotificationRedisConstants.EVENT_DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.debug("重复事件, 丢弃: eventId={}, eventType={}", event.getEventId(), event.getEventType());
            return;
        }
        try {
            dispatch(event);
        } catch (RuntimeException e) {
            // 落库失败要撤掉去重标记，否则重试会被自己挡掉
            clearDedupKey(dedupKey);
            throw e;
        }
    }

    private void dispatch(NotificationEventEnvelope event) {
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.ARTICLE_LIKED -> {
                ArticleLikedPayload payload = payload(event, ArticleLikedPayload.class);
                requireId(payload.getArticleId(), "articleId");
                requireId(payload.getUserId(), "userId");
                requireId(payload.getAuthorId(), "authorId");
                createAndPush(payload.getAuthorId(), payload.getUserId(),
                        NotificationTypeConstants.LIKE, NotificationTargetTypeConstants.ARTICLE, payload.getArticleId());
            }
            case MqRoutingKeyConstants.ANSWER_LIKED -> {
                AnswerLikedPayload payload = payload(event, AnswerLikedPayload.class);
                requireId(payload.getAnswerId(), "answerId");
                requireId(payload.getUserId(), "userId");
                requireId(payload.getAuthorId(), "authorId");
                createAndPush(payload.getAuthorId(), payload.getUserId(),
                        NotificationTypeConstants.LIKE, NotificationTargetTypeConstants.ANSWER, payload.getAnswerId());
            }
            case MqRoutingKeyConstants.COMMENT_CREATED -> {
                CommentCreatedPayload payload = payload(event, CommentCreatedPayload.class);
                requireId(payload.getTargetType(), "targetType");
                requireId(payload.getTargetId(), "targetId");
                requireId(payload.getUserId(), "userId");
                requireId(payload.getReceiverId(), "receiverId");
                createAndPush(payload.getReceiverId(), payload.getUserId(),
                        NotificationTypeConstants.COMMENT, payload.getTargetType(), payload.getTargetId());
            }
            case MqRoutingKeyConstants.ANSWER_CREATED -> {
                AnswerCreatedPayload payload = payload(event, AnswerCreatedPayload.class);
                requireId(payload.getQuestionId(), "questionId");
                requireId(payload.getUserId(), "userId");
                requireId(payload.getQuestionAuthorId(), "questionAuthorId");
                createAndPush(payload.getQuestionAuthorId(), payload.getUserId(),
                        NotificationTypeConstants.ANSWER, NotificationTargetTypeConstants.QUESTION, payload.getQuestionId());
            }
            case MqRoutingKeyConstants.USER_FOLLOWED -> {
                UserFollowedPayload payload = payload(event, UserFollowedPayload.class);
                requireId(payload.getFollowerId(), "followerId");
                requireId(payload.getFolloweeId(), "followeeId");
                createAndPush(payload.getFolloweeId(), payload.getFollowerId(),
                        NotificationTypeConstants.FOLLOW, NotificationTargetTypeConstants.USER, payload.getFolloweeId());
            }
            case MqRoutingKeyConstants.ORDER_PAID -> {
                OrderPaidPayload payload = payload(event, OrderPaidPayload.class);
                requireId(payload.getColumnId(), "columnId");
                requireId(payload.getUserId(), "userId");
                requireId(payload.getColumnAuthorId(), "columnAuthorId");
                createAndPush(payload.getColumnAuthorId(), payload.getUserId(),
                        NotificationTypeConstants.COLUMN_PURCHASE, NotificationTargetTypeConstants.COLUMN, payload.getColumnId());
            }
            case MqRoutingKeyConstants.AI_COMPLETED -> {
                AgentAnalysisCompletedPayload payload = payload(event, AgentAnalysisCompletedPayload.class);
                requireId(payload.getArticleId(), "articleId");
                requireId(payload.getAuthorId(), "authorId");
                // 系统触发，没有 sender
                createAndPush(payload.getAuthorId(), null,
                        NotificationTypeConstants.AI_ANALYSIS, NotificationTargetTypeConstants.ARTICLE, payload.getArticleId());
            }
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }

    /** 落库成功才推送；推送方法内部吞掉异常，不影响消费幂等 */
    private void createAndPush(Long userId, Long senderId, int type, int targetType, Long targetId) {
        Notification notification = notificationService.create(userId, senderId, type, targetType, targetId);
        if (notification != null) {
            notificationPushService.push(notification);
        }
    }

    private boolean handled(String eventType) {
        return MqRoutingKeyConstants.ARTICLE_LIKED.equals(eventType)
                || MqRoutingKeyConstants.ANSWER_LIKED.equals(eventType)
                || MqRoutingKeyConstants.COMMENT_CREATED.equals(eventType)
                || MqRoutingKeyConstants.ANSWER_CREATED.equals(eventType)
                || MqRoutingKeyConstants.USER_FOLLOWED.equals(eventType)
                || MqRoutingKeyConstants.ORDER_PAID.equals(eventType)
                || MqRoutingKeyConstants.AI_COMPLETED.equals(eventType);
    }

    private <T> T payload(NotificationEventEnvelope event, Class<T> type) {
        Map<String, Object> payload = event.getPayload();
        if (payload == null) {
            throw new IllegalArgumentException("payload 缺失, eventType=" + event.getEventType());
        }
        return objectMapper.convertValue(payload, type);
    }

    private void requireId(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 缺失");
        }
    }

    private void clearDedupKey(String dedupKey) {
        try {
            stringRedisTemplate.delete(dedupKey);
        } catch (Exception e) {
            log.warn("去重标记清除失败, 等 TTL 自愈: key={}", dedupKey, e);
        }
    }
}
