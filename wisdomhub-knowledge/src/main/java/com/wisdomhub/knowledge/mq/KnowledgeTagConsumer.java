package com.wisdomhub.knowledge.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import com.wisdomhub.knowledge.config.KnowledgeMqConfiguration;
import com.wisdomhub.knowledge.service.GraphProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消费 content 的标签事件，把节点增改删投影到 Neo4j。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTagConsumer {

    private final GraphProjectionService graphProjectionService;

    @RabbitListener(queues = KnowledgeMqConfiguration.TAG_QUEUE)
    public void onTagEvent(TagEventEnvelope event, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(TagEventEnvelope event) {
        TagEventPayload payload = event.getPayload();
        if (payload == null || payload.getTagId() == null) {
            // 残缺消息重试耗尽后进死信，别静默 ACK
            throw new IllegalArgumentException("标签事件缺少 tagId, eventType=" + event.getEventType());
        }
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.TAG_CREATED, MqRoutingKeyConstants.TAG_UPDATED ->
                    graphProjectionService.upsertTag(payload);
            case MqRoutingKeyConstants.TAG_DELETED -> graphProjectionService.deleteTag(payload.getTagId());
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }
}
