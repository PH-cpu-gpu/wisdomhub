package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.AnswerLikedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 回答点赞。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AnswerLikedEvent extends BaseEvent<AnswerLikedPayload> {

    public AnswerLikedEvent(AnswerLikedPayload payload) {
        super(MqRoutingKeyConstants.ANSWER_LIKED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ANSWER_LIKED;
    }
}
