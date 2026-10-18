package com.wisdomhub.recommendation.mq;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.recommendation.config.RecommendationMqConfiguration;
import com.wisdomhub.recommendation.constants.RecommendationRedisConstants;
import com.wisdomhub.recommendation.service.InterestProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 行为采集消费端：eventId 去重后写入画像 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final StringRedisTemplate stringRedisTemplate;
    private final InterestProfileService interestProfileService;

    @RabbitListener(queues = RecommendationMqConfiguration.BEHAVIOR_QUEUE)
    public void onEvent(BehaviorEventEnvelope event) {
        if (!MqRoutingKeyConstants.BEHAVIOR_RECORDED.equals(event.getEventType())) {
            log.debug("忽略不关心的路由键: {}", event.getEventType());
            return;
        }
        BehaviorRecordedPayload payload = event.getPayload();
        if (payload == null || payload.getUserId() == null || StrUtil.isBlank(payload.getBehaviorType())) {
            throw new IllegalArgumentException("行为事件字段缺失, eventId=" + event.getEventId());
        }
        String dedupKey = RecommendationRedisConstants.BEHAVIOR_DEDUP_KEY + event.getEventId();
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", RecommendationRedisConstants.BEHAVIOR_DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.debug("重复行为事件, 丢弃: eventId={}", event.getEventId());
            return;
        }
        try {
            interestProfileService.record(payload);
        } catch (RuntimeException e) {
            // 写失败要撤掉去重标记，否则重试会被自己挡掉
            stringRedisTemplate.delete(dedupKey);
            throw e;
        }
    }
}
