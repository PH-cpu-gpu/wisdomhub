package com.wisdomhub.notification.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

/**
 * 一条队列收全部通知事件，只能有一个监听方法。payload 先按 Map 接，再按 eventType 转成强类型。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NotificationEventEnvelope extends BaseEvent<Map<String, Object>> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
