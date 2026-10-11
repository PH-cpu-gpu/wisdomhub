package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 新建标签。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagCreatedEvent extends BaseEvent<TagEventPayload> {

    public TagCreatedEvent(TagEventPayload payload) {
        super(MqRoutingKeyConstants.TAG_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.TAG_CREATED;
    }
}
