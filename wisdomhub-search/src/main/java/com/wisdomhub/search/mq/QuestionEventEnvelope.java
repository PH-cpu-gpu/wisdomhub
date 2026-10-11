package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.QuestionEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 一个队列上收 question.created / question.deleted 两种事件，只能有一个监听方法
 * （多个 @RabbitListener 会变成抢同一条队列的竞争消费者）。删除事件的 payload 是内容快照的子集。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QuestionEventEnvelope extends BaseEvent<QuestionEventPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
