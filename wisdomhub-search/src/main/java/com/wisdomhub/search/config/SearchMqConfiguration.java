package com.wisdomhub.search.config;

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
 * 队列与死信由消费者自己声明。
 * 死信三件套缺一不可：只把失败消息 reject 掉而不声明 .dlq，消息会直接消失且不报错。
 */
@Configuration
public class SearchMqConfiguration {

    public static final String ARTICLE_QUEUE = "search.article.q";
    public static final String ARTICLE_DLQ = ARTICLE_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String QUESTION_QUEUE = "search.question.q";
    public static final String QUESTION_DLQ = QUESTION_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String USER_QUEUE = "search.user.q";
    public static final String USER_DLQ = USER_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String TAG_QUEUE = "search.tag.q";
    public static final String TAG_DLQ = TAG_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue searchArticleQueue() {
        return QueueBuilder.durable(ARTICLE_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(ARTICLE_DLQ)
                .build();
    }

    @Bean
    public Queue searchArticleDeadLetterQueue() {
        return QueueBuilder.durable(ARTICLE_DLQ).build();
    }

    @Bean
    public Binding searchArticleBinding(Queue searchArticleQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(searchArticleQueue).to(eventExchange).with("article.*");
    }

    @Bean
    public Binding searchArticleDeadLetterBinding(Queue searchArticleDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(searchArticleDeadLetterQueue).to(deadLetterExchange).with(ARTICLE_DLQ);
    }

    // ==================== question / user / tag ====================

    @Bean
    public Queue searchQuestionQueue() {
        return QueueBuilder.durable(QUESTION_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(QUESTION_DLQ)
                .build();
    }

    @Bean
    public Queue searchQuestionDeadLetterQueue() {
        return QueueBuilder.durable(QUESTION_DLQ).build();
    }

    @Bean
    public Binding searchQuestionBinding(Queue searchQuestionQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(searchQuestionQueue).to(eventExchange).with("question.*");
    }

    @Bean
    public Binding searchQuestionDeadLetterBinding(Queue searchQuestionDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(searchQuestionDeadLetterQueue).to(deadLetterExchange).with(QUESTION_DLQ);
    }

    @Bean
    public Queue searchUserQueue() {
        return QueueBuilder.durable(USER_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(USER_DLQ)
                .build();
    }

    @Bean
    public Queue searchUserDeadLetterQueue() {
        return QueueBuilder.durable(USER_DLQ).build();
    }

    @Bean
    public Binding searchUserBinding(Queue searchUserQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(searchUserQueue).to(eventExchange).with("user.*");
    }

    @Bean
    public Binding searchUserDeadLetterBinding(Queue searchUserDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(searchUserDeadLetterQueue).to(deadLetterExchange).with(USER_DLQ);
    }

    @Bean
    public Queue searchTagQueue() {
        return QueueBuilder.durable(TAG_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(TAG_DLQ)
                .build();
    }

    @Bean
    public Queue searchTagDeadLetterQueue() {
        return QueueBuilder.durable(TAG_DLQ).build();
    }

    @Bean
    public Binding searchTagBinding(Queue searchTagQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(searchTagQueue).to(eventExchange).with("tag.*");
    }

    @Bean
    public Binding searchTagDeadLetterBinding(Queue searchTagDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(searchTagDeadLetterQueue).to(deadLetterExchange).with(TAG_DLQ);
    }
}
