package com.wisdomhub.knowledge.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.event.TagRelationChangedEvent;
import com.wisdomhub.common.mq.event.payload.TagRelationChangedPayload;
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
 * 消费 content 的关系变更事件，按 action 增删 Neo4j 边。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRelationConsumer {

    private final GraphProjectionService graphProjectionService;

    @RabbitListener(queues = KnowledgeMqConfiguration.RELATION_QUEUE)
    public void onRelationEvent(TagRelationChangedEvent event, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(TagRelationChangedEvent event) {
        TagRelationChangedPayload payload = event.getPayload();
        if (payload == null || payload.getAction() == null) {
            throw new IllegalArgumentException("关系事件缺少 payload 或 action, eventType=" + event.getEventType());
        }
        switch (payload.getAction()) {
            case TagRelationChangedPayload.ACTION_CREATED -> graphProjectionService.upsertRelation(payload);
            case TagRelationChangedPayload.ACTION_DELETED -> graphProjectionService.deleteRelation(payload);
            default -> throw new IllegalArgumentException("未知的关系事件 action: " + payload.getAction());
        }
    }
}
