package com.wisdomhub.recommendation.mq;

import cn.hutool.core.util.StrUtil;
import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.recommendation.config.RecommendationMqConfiguration;
import com.wisdomhub.recommendation.service.InterestProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 行为采集消费端：eventId 去重与画像写入在同一个脚本内完成 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final InterestProfileService interestProfileService;

    @RabbitListener(queues = RecommendationMqConfiguration.BEHAVIOR_QUEUE)
    public void onEvent(BehaviorEventEnvelope event, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(BehaviorEventEnvelope event) {
        if (!MqRoutingKeyConstants.BEHAVIOR_RECORDED.equals(event.getEventType())) {
            log.debug("忽略不关心的路由键: {}", event.getEventType());
            return;
        }
        BehaviorRecordedPayload payload = event.getPayload();
        if (payload == null || payload.getUserId() == null || StrUtil.isBlank(payload.getBehaviorType())) {
            throw new IllegalArgumentException("行为事件字段缺失, eventId=" + event.getEventId());
        }
        if (!interestProfileService.record(event.getEventId(), payload)) {
            log.debug("行为事件未写入（重复或类型未知）: eventId={}", event.getEventId());
        }
    }
}
