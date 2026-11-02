package com.wisdomhub.knowledge.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 一个队列收 tag.created / tag.updated / tag.deleted 三种事件；删除事件只有 tagId，其余字段为空。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagEventEnvelope extends BaseEvent<TagEventPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
