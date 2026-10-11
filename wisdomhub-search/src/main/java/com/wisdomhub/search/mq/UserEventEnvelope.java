package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.UserEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * user.* 目前只有 user.updated 需要进索引，但队列绑的是 user.*：
 * 用信封接收，user.registered / user.followed 将来真发出来了也只是被记一行 debug 丢掉。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserEventEnvelope extends BaseEvent<UserEventPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
