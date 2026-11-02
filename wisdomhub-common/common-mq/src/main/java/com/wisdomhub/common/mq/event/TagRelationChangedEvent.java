package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagRelationChangedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 标签关系新增或删除。action 在 payload 里，消费方按它增删 Neo4j 边。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagRelationChangedEvent extends BaseEvent<TagRelationChangedPayload> {

    public TagRelationChangedEvent(TagRelationChangedPayload payload) {
        super(MqRoutingKeyConstants.TAG_RELATION_CHANGED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.TAG_RELATION_CHANGED;
    }
}
