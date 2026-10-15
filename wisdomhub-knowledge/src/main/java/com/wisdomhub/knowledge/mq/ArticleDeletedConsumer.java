package com.wisdomhub.knowledge.mq;

import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.knowledge.config.KnowledgeMqConfiguration;
import com.wisdomhub.knowledge.service.GraphArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 article.deleted，把该文章清出图谱（节点 + ABOUT 边）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleDeletedConsumer {

    private final GraphArticleService graphArticleService;

    @RabbitListener(queues = KnowledgeMqConfiguration.ARTICLE_QUEUE)
    public void onArticleDeleted(ArticleDeletedEvent event) {
        Long articleId = event.getPayload() == null ? null : event.getPayload().getArticleId();
        if (articleId == null) {
            // payload 残缺没法处理，抛出去让它重试耗尽后进死信，别静默 ACK 掉
            throw new IllegalArgumentException("articleId 缺失, eventId=" + event.getEventId());
        }
        graphArticleService.deleteArticle(articleId);
    }
}
