package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.CommentCreatedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 评论发表。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CommentCreatedEvent extends BaseEvent<CommentCreatedPayload> {

    public CommentCreatedEvent(CommentCreatedPayload payload) {
        super(MqRoutingKeyConstants.COMMENT_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.COMMENT_CREATED;
    }
}
