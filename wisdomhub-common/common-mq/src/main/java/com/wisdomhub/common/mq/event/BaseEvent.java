package com.wisdomhub.common.mq.event;

import cn.hutool.core.lang.UUID;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
public abstract class BaseEvent<T> {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    private int version = 1;
    private T payload;

    protected BaseEvent(String eventType, T payload) {
        this.eventType = eventType;
        this.payload = payload;
    }

    protected BaseEvent() {}

    public abstract String routingKey();
}
