package com.wisdomhub.search.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.search.api.ContentFeign;
import com.wisdomhub.search.api.dto.ArticleInternalDTO;
import com.wisdomhub.search.api.dto.ArticleTagDTO;
import com.wisdomhub.search.config.SearchMqConfiguration;
import com.wisdomhub.search.domain.document.ArticleIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 消费 article.* 事件维护 article_index。
 * <p>
 * 总原则：<b>一切以回查为准，payload 只提供 articleId</b>。回查拿到的是权威源的当前状态，
 * 所以消息乱序也不会倒退（两条 created/updated 各自回查，写进去的内容完全一样）。
 * 代价是一条硬约束：/internal/content/article/{id} 永远不能加缓存，否则这条防线整条失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleIndexConsumer {

    /** 与 content-service 的 ArticleConstants.PUBLISH 同值 */
    private static final int STATE_PUBLISH = 2;

    private final ContentFeign contentFeign;
    private final EsIndexService esIndexService;

    @RabbitListener(queues = SearchMqConfiguration.ARTICLE_QUEUE)
    public void onArticleEvent(ArticleEventEnvelope event, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(ArticleEventEnvelope event) {
        Long articleId = event.getPayload() == null ? null : event.getPayload().getArticleId();
        if (articleId == null) {
            // payload 残缺没法处理，抛出去让它重试耗尽后进死信，别静默 ACK 掉
            throw new IllegalArgumentException("articleId 缺失, eventType=" + event.getEventType());
        }
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.ARTICLE_CREATED, MqRoutingKeyConstants.ARTICLE_UPDATED ->
                    reloadAndIndex(articleId);
            case MqRoutingKeyConstants.ARTICLE_DELETED -> esIndexService.deleteArticle(articleId);
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }

    private void reloadAndIndex(Long articleId) {
        ArticleInternalDTO article;
        try {
            article = contentFeign.getArticle(articleId);
        } catch (BusinessException e) {
            if (e.getErrorCode().getCode() == CommonErrorCode.NOT_FOUND.getCode()) {
                log.info("文章已不存在，按删除处理, articleId={}", articleId);
                esIndexService.deleteArticle(articleId);
                return;
            }
            // 5xx / 正文缺失等：抛出去重试，绝不能索引一篇没有正文的文章
            throw e;
        }
        if (article == null || article.getState() == null || article.getState() != STATE_PUBLISH) {
            log.info("文章非已发布状态，按删除处理, articleId={}, state={}", articleId, article == null ? null : article.getState());
            esIndexService.deleteArticle(articleId);
            return;
        }
        esIndexService.indexArticle(toDoc(article));
    }

    private ArticleIndexDoc toDoc(ArticleInternalDTO article) {
        List<ArticleIndexDoc.Tag> tags = article.getTags() == null ? List.of()
                : article.getTags().stream().map(this::toTag).toList();
        return ArticleIndexDoc.builder()
                .articleId(article.getArticleId())
                .title(article.getTitle())
                .suggest(article.getTitle())
                .summary(article.getSummary())
                .content(article.getMarkdown())
                .authorId(article.getAuthorId())
                .tags(tags)
                .state(article.getState())
                .createTime(article.getCreateTime())
                .publishTime(article.getPublishTime())
                .updateTime(article.getUpdateTime())
                .viewCount(article.getViewCount())
                .likeCount(article.getLikeCount())
                .commentCount(article.getCommentCount())
                .favoriteCount(article.getFavoriteCount())
                .build();
    }

    private ArticleIndexDoc.Tag toTag(ArticleTagDTO tag) {
        return ArticleIndexDoc.Tag.builder()
                .tagId(tag.getTagId())
                .name(tag.getName())
                .category(tag.getCategory())
                .build();
    }
}
