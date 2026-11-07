package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TargetDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TargetDeletedEvent extends BaseEvent<TargetDeletedPayload> {

    public TargetDeletedEvent(TargetDeletedPayload payload) {
        super(MqRoutingKeyConstants.TARGET_DELETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.TARGET_DELETED;
    }
}
