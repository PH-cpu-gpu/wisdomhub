package com.wisdomhub.search.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import com.wisdomhub.search.config.SearchMqConfiguration;
import com.wisdomhub.search.domain.document.TagIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消费 tag.* 事件维护 tag_index。改名与删除还要顺手修 article_index
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagIndexConsumer {

    private final EsIndexService esIndexService;

    @RabbitListener(queues = SearchMqConfiguration.TAG_QUEUE)
    public void onTagEvent(TagEventEnvelope event, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(TagEventEnvelope event) {
        Long tagId = requireTagId(event);
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.TAG_CREATED -> esIndexService.indexTag(toDoc(event.getPayload()));
            case MqRoutingKeyConstants.TAG_UPDATED -> {
                TagEventPayload payload = event.getPayload();
                esIndexService.indexTag(toDoc(payload));
                esIndexService.renameTagInArticles(tagId, payload.getName(), payload.getCategory());
            }
            case MqRoutingKeyConstants.TAG_DELETED -> {
                esIndexService.deleteTag(tagId);
                esIndexService.removeTagFromArticles(tagId);
            }
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }

    /** payload 残缺没法处理，抛出去让它重试耗尽后进死信，别静默 ACK 掉 */
    private Long requireTagId(TagEventEnvelope event) {
        Long tagId = event.getPayload() == null ? null : event.getPayload().getTagId();
        if (tagId == null) {
            throw new IllegalArgumentException("tagId 缺失, eventType=" + event.getEventType());
        }
        return tagId;
    }

    private TagIndexDoc toDoc(TagEventPayload payload) {
        return TagIndexDoc.builder()
                .tagId(payload.getTagId())
                .name(payload.getName())
                .suggest(payload.getName())
                .category(payload.getCategory())
                .description(payload.getDescription())
                .createTime(payload.getCreateTime())
                .updateTime(payload.getUpdateTime())
                .build();
    }
}
