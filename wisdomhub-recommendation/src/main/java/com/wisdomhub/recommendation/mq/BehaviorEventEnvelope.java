package com.wisdomhub.recommendation.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** behavior.recorded 的信封，payload 强类型接收 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BehaviorEventEnvelope extends BaseEvent<BehaviorRecordedPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
