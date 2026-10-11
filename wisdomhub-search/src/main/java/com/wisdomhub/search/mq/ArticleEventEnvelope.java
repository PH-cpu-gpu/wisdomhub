package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.ArticleEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 一个队列上收三种事件，只能有一个监听方法（多个 @RabbitListener 会变成抢同一条队列的竞争消费者，
 * 消息按容器轮转、类型对不上的那个直接反序列化失败）。所以这里用一个具体信封接收，再按 eventType 分派。
 * <p>
 * article.deleted 的 payload（articleId + authorId）是 ArticleEventPayload 的子集，用它一起接得住：
 * 多余的字段为 null，而删除路径根本不读它们。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArticleEventEnvelope extends BaseEvent<ArticleEventPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
