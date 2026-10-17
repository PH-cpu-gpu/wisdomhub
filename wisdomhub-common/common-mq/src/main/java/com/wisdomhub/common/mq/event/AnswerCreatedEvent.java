package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.AnswerCreatedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 回答发表。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AnswerCreatedEvent extends BaseEvent<AnswerCreatedPayload> {

    public AnswerCreatedEvent(AnswerCreatedPayload payload) {
        super(MqRoutingKeyConstants.ANSWER_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ANSWER_CREATED;
    }
}
