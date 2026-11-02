package com.wisdomhub.knowledge.config;

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
 * 队列与死信由消费者自己声明。
 */
@Configuration
public class KnowledgeMqConfiguration {

    public static final String EXTRACT_QUEUE = "knowledge.extract.q";
    public static final String EXTRACT_DLQ = EXTRACT_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String ARTICLE_QUEUE = "knowledge.article.q";
    public static final String ARTICLE_DLQ = ARTICLE_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String TAG_QUEUE = "knowledge.tag.q";
    public static final String TAG_DLQ = TAG_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    public static final String RELATION_QUEUE = "knowledge.relation.q";
    public static final String RELATION_DLQ = RELATION_QUEUE + MqTopologyConstants.DLQ_SUFFIX;

    @Bean
    public Queue knowledgeExtractQueue() {
        return QueueBuilder.durable(EXTRACT_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(EXTRACT_DLQ)
                .build();
    }

    @Bean
    public Queue knowledgeExtractDeadLetterQueue() {
        return QueueBuilder.durable(EXTRACT_DLQ).build();
    }

    @Bean
    public Binding knowledgeExtractBinding(Queue knowledgeExtractQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(knowledgeExtractQueue).to(eventExchange).with(MqRoutingKeyConstants.KNOWLEDGE_EXTRACT);
    }

    @Bean
    public Binding knowledgeExtractDeadLetterBinding(Queue knowledgeExtractDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(knowledgeExtractDeadLetterQueue).to(deadLetterExchange).with(EXTRACT_DLQ);
    }

    @Bean
    public Queue knowledgeArticleQueue() {
        return QueueBuilder.durable(ARTICLE_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(ARTICLE_DLQ)
                .build();
    }

    @Bean
    public Queue knowledgeArticleDeadLetterQueue() {
        return QueueBuilder.durable(ARTICLE_DLQ).build();
    }

    @Bean
    public Binding knowledgeArticleBinding(Queue knowledgeArticleQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(knowledgeArticleQueue).to(eventExchange).with(MqRoutingKeyConstants.ARTICLE_DELETED);
    }

    @Bean
    public Binding knowledgeArticleDeadLetterBinding(Queue knowledgeArticleDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(knowledgeArticleDeadLetterQueue).to(deadLetterExchange).with(ARTICLE_DLQ);
    }

    @Bean
    public Queue knowledgeTagQueue() {
        return QueueBuilder.durable(TAG_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(TAG_DLQ)
                .build();
    }

    @Bean
    public Queue knowledgeTagDeadLetterQueue() {
        return QueueBuilder.durable(TAG_DLQ).build();
    }

    @Bean
    public Binding knowledgeTagBinding(Queue knowledgeTagQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(knowledgeTagQueue).to(eventExchange).with("tag.*");
    }

    @Bean
    public Binding knowledgeTagDeadLetterBinding(Queue knowledgeTagDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(knowledgeTagDeadLetterQueue).to(deadLetterExchange).with(TAG_DLQ);
    }

    @Bean
    public Queue knowledgeRelationQueue() {
        return QueueBuilder.durable(RELATION_QUEUE)
                .deadLetterExchange(MqTopologyConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(RELATION_DLQ)
                .build();
    }

    @Bean
    public Queue knowledgeRelationDeadLetterQueue() {
        return QueueBuilder.durable(RELATION_DLQ).build();
    }

    @Bean
    public Binding knowledgeRelationBinding(Queue knowledgeRelationQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(knowledgeRelationQueue).to(eventExchange)
                .with(MqRoutingKeyConstants.TAG_RELATION_CHANGED);
    }

    @Bean
    public Binding knowledgeRelationDeadLetterBinding(Queue knowledgeRelationDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(knowledgeRelationDeadLetterQueue).to(deadLetterExchange).with(RELATION_DLQ);
    }
}
