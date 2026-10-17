package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.UserFollowedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 关注用户。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserFollowedEvent extends BaseEvent<UserFollowedPayload> {

    public UserFollowedEvent(UserFollowedPayload payload) {
        super(MqRoutingKeyConstants.USER_FOLLOWED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.USER_FOLLOWED;
    }
}
