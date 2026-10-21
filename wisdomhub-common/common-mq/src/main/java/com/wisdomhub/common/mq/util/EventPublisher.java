package com.wisdomhub.common.mq.util;

import com.wisdomhub.common.mq.constants.MqTopologyConstants;
import com.wisdomhub.common.mq.event.BaseEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class EventPublisher {

    private RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(BaseEvent<?> event) {
        send(event, event.routingKey());
    }

    public void send(BaseEvent<?> event, String routingKey) {
        rabbitTemplate.convertAndSend(
                MqTopologyConstants.EVENT_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setMessageId(event.getEventId());
                    return message;
                },
                new CorrelationData(event.getEventId())
        );
    }
}
