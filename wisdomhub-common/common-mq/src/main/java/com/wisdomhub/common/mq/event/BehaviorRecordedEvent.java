package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 用户行为采集，供推荐画像消费 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BehaviorRecordedEvent extends BaseEvent<BehaviorRecordedPayload> {

    public BehaviorRecordedEvent(BehaviorRecordedPayload payload) {
        super(MqRoutingKeyConstants.BEHAVIOR_RECORDED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.BEHAVIOR_RECORDED;
    }
}
