package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.QuestionDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 删除问题。没有它，删掉的问题会永远留在 ES 里。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QuestionDeletedEvent extends BaseEvent<QuestionDeletedPayload> {

    public QuestionDeletedEvent(QuestionDeletedPayload payload) {
        super(MqRoutingKeyConstants.QUESTION_DELETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.QUESTION_DELETED;
    }
}
