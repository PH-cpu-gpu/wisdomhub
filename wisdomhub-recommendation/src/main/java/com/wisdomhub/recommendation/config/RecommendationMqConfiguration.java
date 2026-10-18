package com.wisdomhub.recommendation.config;

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

/** 行为队列与死信三件套，只绑 behavior.recorded */
@Configuration
public class RecommendationMqConfiguration {

    public static final String BEHAVIOR_QUEUE = "recommendation.behavior.q";
    public static final String BEHAVIOR_DLQ = BEHAVIOR_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue recommendationBehaviorQueue() {
        return QueueBuilder.durable(BEHAVIOR_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(BEHAVIOR_DLQ)
                .build();
    }

    @Bean
    public Queue recommendationBehaviorDeadLetterQueue() {
        return QueueBuilder.durable(BEHAVIOR_DLQ).build();
    }

    @Bean
    public Binding recommendationBehaviorBinding(Queue recommendationBehaviorQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(recommendationBehaviorQueue).to(eventExchange)
                .with(MqRoutingKeyConstants.BEHAVIOR_RECORDED);
    }

    @Bean
    public Binding recommendationBehaviorDeadLetterBinding(Queue recommendationBehaviorDeadLetterQueue,
                                                           DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(recommendationBehaviorDeadLetterQueue).to(deadLetterExchange).with(BEHAVIOR_DLQ);
    }
}
