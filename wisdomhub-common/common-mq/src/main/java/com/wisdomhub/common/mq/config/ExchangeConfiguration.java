package com.wisdomhub.common.mq.config;

import com.wisdomhub.common.mq.constants.MqTopologyConstants;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ExchangeConfiguration {

    @Bean
    public TopicExchange eventExchange() {
        return ExchangeBuilder
                .topicExchange(MqTopologyConstants.EVENT_EXCHANGE)
                .durable(true)
                .build();
    }
}
