package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.UserEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 用户资料变更（用户名 / 头像 / 简介）。
 * 绑邮箱与手机号不发 —— 它们不在任何索引里，没必要让联系方式离开 user-service。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserUpdatedEvent extends BaseEvent<UserEventPayload> {

    public UserUpdatedEvent(UserEventPayload payload) {
        super(MqRoutingKeyConstants.USER_UPDATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.USER_UPDATED;
    }
}
