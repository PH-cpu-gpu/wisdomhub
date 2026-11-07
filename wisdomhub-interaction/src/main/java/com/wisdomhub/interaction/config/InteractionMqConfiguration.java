package com.wisdomhub.interaction.config;

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

/** interaction 消费的队列与死信由自己声明。 */
@Configuration
public class InteractionMqConfiguration {

    public static final String TARGET_DELETED_QUEUE = "interaction.target.deleted.q";
    public static final String TARGET_DELETED_DLQ = TARGET_DELETED_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue targetDeletedQueue() {
        return QueueBuilder.durable(TARGET_DELETED_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(TARGET_DELETED_DLQ)
                .build();
    }

    @Bean
    public Queue targetDeletedDeadLetterQueue() {
        return QueueBuilder.durable(TARGET_DELETED_DLQ).build();
    }

    @Bean
    public Binding targetDeletedBinding(Queue targetDeletedQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(targetDeletedQueue).to(eventExchange)
                .with(MqRoutingKeyConstants.TARGET_DELETED);
    }

    @Bean
    public Binding targetDeletedDeadLetterBinding(Queue targetDeletedDeadLetterQueue,
                                                  DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(targetDeletedDeadLetterQueue).to(deadLetterExchange)
                .with(TARGET_DELETED_DLQ);
    }
}
