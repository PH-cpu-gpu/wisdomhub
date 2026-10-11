package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.QuestionEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 提问。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QuestionCreatedEvent extends BaseEvent<QuestionEventPayload> {

    public QuestionCreatedEvent(QuestionEventPayload payload) {
        super(MqRoutingKeyConstants.QUESTION_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.QUESTION_CREATED;
    }
}
