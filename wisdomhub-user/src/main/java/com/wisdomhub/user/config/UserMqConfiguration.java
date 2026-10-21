package com.wisdomhub.user.config;

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

/** 浏览历史清理队列与死信三件套（B2.31） */
@Configuration
public class UserMqConfiguration {

    public static final String BROWSE_HISTORY_QUEUE = "user.browse-history.q";
    public static final String BROWSE_HISTORY_DLQ = BROWSE_HISTORY_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue browseHistoryQueue() {
        return QueueBuilder.durable(BROWSE_HISTORY_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(BROWSE_HISTORY_DLQ)
                .build();
    }

    @Bean
    public Queue browseHistoryDeadLetterQueue() {
        return QueueBuilder.durable(BROWSE_HISTORY_DLQ).build();
    }

    @Bean
    public Binding browseHistoryBinding(Queue browseHistoryQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(browseHistoryQueue).to(eventExchange)
                .with(MqRoutingKeyConstants.ARTICLE_DELETED);
    }

    @Bean
    public Binding browseHistoryDeadLetterBinding(Queue browseHistoryDeadLetterQueue,
                                                  DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(browseHistoryDeadLetterQueue).to(deadLetterExchange)
                .with(BROWSE_HISTORY_DLQ);
    }
}
