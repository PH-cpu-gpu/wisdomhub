package com.wisdomhub.notification.config;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.constants.MqTopologyConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知队列与死信三件套。绑定刻意放宽（*.liked / *.created），未登记的路由键由消费者忽略。
 */
@Configuration
public class NotificationMqConfiguration {

    public static final String NOTIFICATION_QUEUE = "notification.event.q";
    public static final String NOTIFICATION_DLQ = NOTIFICATION_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue notificationEventQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue notificationEventDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationEventLikeBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with("*.liked");
    }

    @Bean
    public Binding notificationEventCollectedBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with("*.collected");
    }

    @Bean
    public Binding notificationEventCreatedBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with("*.created");
    }

    @Bean
    public Binding notificationEventFollowedBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with(MqRoutingKeyConstants.USER_FOLLOWED);
    }

    @Bean
    public Binding notificationEventOrderPaidBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with(MqRoutingKeyConstants.ORDER_PAID);
    }

    @Bean
    public Binding notificationEventAiCompletedBinding(Queue notificationEventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(eventExchange).with(MqRoutingKeyConstants.AI_COMPLETED);
    }

    @Bean
    public Binding notificationEventDeadLetterBinding(Queue notificationEventDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationEventDeadLetterQueue).to(deadLetterExchange).with(NOTIFICATION_DLQ);
    }
}
