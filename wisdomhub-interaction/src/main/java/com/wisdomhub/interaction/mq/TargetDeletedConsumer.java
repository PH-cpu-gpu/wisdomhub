package com.wisdomhub.interaction.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TargetDeletedPayload;
import com.wisdomhub.interaction.config.InteractionMqConfiguration;
import com.wisdomhub.interaction.service.TargetDeletedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 消费 target.deleted，清理该目标的评论、点赞、收藏与计数。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetDeletedConsumer {

    private final TargetDeletedService targetDeletedService;

    @RabbitListener(queues = InteractionMqConfiguration.TARGET_DELETED_QUEUE)
    public void onEvent(TargetDeletedEnvelope event, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(TargetDeletedEnvelope event) {
        if (!MqRoutingKeyConstants.TARGET_DELETED.equals(event.getEventType())) {
            log.debug("忽略不关心的路由键: {}", event.getEventType());
            return;
        }
        TargetDeletedPayload payload = event.getPayload();
        if (payload == null || payload.getTargetType() == null || payload.getTargetId() == null) {
            throw new IllegalArgumentException("target.deleted 字段缺失, eventId=" + event.getEventId());
        }
        if (!ContentTargetTypeConstants.isValid(payload.getTargetType())) {
            throw new IllegalArgumentException("未知 targetType: " + payload.getTargetType());
        }
        targetDeletedService.purge(payload.getTargetType(), payload.getTargetId());
    }
}
