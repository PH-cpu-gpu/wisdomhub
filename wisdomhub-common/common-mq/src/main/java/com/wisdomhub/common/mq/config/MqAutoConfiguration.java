package com.wisdomhub.common.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.common.mq.util.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
public class MqAutoConfiguration {

    @Bean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate) {
        return new EventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** 生产者 confirm/return 观测；manual ACK 下重试耗尽后必须用 rejectManual=true 的异常才能进 .dlq */
    @Bean
    public RabbitTemplateCustomizer rabbitReliabilityCustomizer() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (ack) {
                    log.debug("消息已被 broker 确认, eventId={}",
                            correlation == null ? null : correlation.getId());
                    return;
                }
                log.error("消息未被 broker 确认, eventId={}, cause={}",
                        correlation == null ? null : correlation.getId(), cause);
            });
            template.setReturnsCallback(returned -> log.error(
                    "消息无法路由, eventId={}, exchange={}, routingKey={}, replyCode={}, replyText={}",
                    messageId(returned), returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText()));
        };
    }

    @Bean
    @ConditionalOnMissingBean(MessageRecoverer.class)
    public MessageRecoverer rejectManualMessageRecoverer() {
        return (message, cause) -> {
            log.error("消费重试耗尽, eventId={}, 进入死信",
                    message.getMessageProperties().getMessageId(), cause);
            throw new AmqpRejectAndDontRequeueException("消费重试耗尽，进入死信", true, cause);
        };
    }

    private static String messageId(ReturnedMessage returned) {
        return returned.getMessage() == null
                ? null
                : returned.getMessage().getMessageProperties().getMessageId();
    }
}
