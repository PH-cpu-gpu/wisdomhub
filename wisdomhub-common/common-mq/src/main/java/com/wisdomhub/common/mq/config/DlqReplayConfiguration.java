package com.wisdomhub.common.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.common.mq.dlq.DeadLetterReplayService;
import com.wisdomhub.common.mq.dlq.DlqReplayProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(DlqReplayProperties.class)
@ConditionalOnProperty(prefix = "wisdomhub.mq.dlq-replay", name = "enabled", havingValue = "true")
public class DlqReplayConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterReplayService deadLetterReplayService(ConnectionFactory connectionFactory,
                                                           AmqpAdmin amqpAdmin,
                                                           ObjectMapper objectMapper,
                                                           DlqReplayProperties properties) {
        return new DeadLetterReplayService(connectionFactory, amqpAdmin, objectMapper, properties);
    }
}
