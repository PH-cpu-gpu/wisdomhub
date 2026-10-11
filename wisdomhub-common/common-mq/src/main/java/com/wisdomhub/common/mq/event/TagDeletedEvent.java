package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 删除标签（物理删除）。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagDeletedEvent extends BaseEvent<TagDeletedPayload> {

    public TagDeletedEvent(TagDeletedPayload payload) {
        super(MqRoutingKeyConstants.TAG_DELETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.TAG_DELETED;
    }
}
