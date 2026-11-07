package com.wisdomhub.interaction.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.TargetDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TargetDeletedEnvelope extends BaseEvent<TargetDeletedPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
