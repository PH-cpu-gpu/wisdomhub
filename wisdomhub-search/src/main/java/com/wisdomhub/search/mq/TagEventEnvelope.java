package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 一个队列上收 tag.created / tag.updated / tag.deleted 三种事件（理由同 ArticleEventEnvelope）。
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
