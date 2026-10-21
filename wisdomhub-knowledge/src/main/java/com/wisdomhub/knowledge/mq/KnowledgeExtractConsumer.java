package com.wisdomhub.knowledge.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.event.KnowledgeExtractEvent;
import com.wisdomhub.common.mq.event.payload.KnowledgeExtractPayload;
import com.wisdomhub.knowledge.config.KnowledgeMqConfiguration;
import com.wisdomhub.knowledge.service.GraphArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 消费 Agent 抽取结果，按 articleId 整篇重建 ABOUT 边。
 * 只产 Article → Tag：Tag → Tag 是人工维护的，不让 LLM 往里加边。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeExtractConsumer {

    private final GraphArticleService graphArticleService;

    @RabbitListener(queues = KnowledgeMqConfiguration.EXTRACT_QUEUE)
    public void onKnowledgeExtract(KnowledgeExtractEvent event, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(KnowledgeExtractEvent event) {
        KnowledgeExtractPayload payload = event.getPayload();
        if (payload == null || payload.getArticleId() == null || payload.getEntities() == null) {
            // 残缺消息没法处理，抛出去让它重试耗尽后进死信，别静默 ACK 掉
            throw new IllegalArgumentException("抽取事件残缺, eventId=" + event.getEventId());
        }
        List<String> names = payload.getEntities().stream().map(entity -> entity == null ? null : entity.getName()).toList();
        graphArticleService.rebuildAbout(payload.getArticleId(), names);
    }
}
