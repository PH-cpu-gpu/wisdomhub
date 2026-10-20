package com.wisdomhub.search.service.reconcile;

import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.common.mq.event.ArticleUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleDeletedPayload;
import com.wisdomhub.common.mq.event.payload.ArticleEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.search.api.ContentFeign;
import com.wisdomhub.search.api.dto.ArticleInternalDTO;
import com.wisdomhub.search.config.ReconcileProperties;
import com.wisdomhub.search.domain.document.ArticleIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * article_index 对账：以 content 的 article 表为准，不一致就重投 article.updated。
 * <p>
 * 增量按 update_time 滑动窗口扫；全量额外扫 ES 找孤儿文档。都复用 search.article.q 的消费链，
 * 重投的事件只带快照，消费者仍会回查一次权威源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReconcileService {

    /** 与 content-service 的 ArticleConstants.PUBLISH 同值 */
    private static final int STATE_PUBLISH = 2;

    /** 全量扫的起点。不用 since=null，避免 Feign 传空参数的分支差异 */
    private static final String EPOCH = "1970-01-01T00:00:00";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContentFeign contentFeign;
    private final EsIndexService esIndexService;
    private final EventPublisher eventPublisher;
    private final ReconcileProperties properties;

    public ReconcileResult reconcile(boolean full) {
        ReconcileResult result = new ReconcileResult();
        Set<Long> sourceIds = new HashSet<>();
        String since = full
                ? EPOCH
                : LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getLookbackMinutes()).format(ISO);
        long lastId = 0L;

        while (true) {
            List<ArticleInternalDTO> page = contentFeign.syncArticles(since, lastId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            result.setScanned(result.getScanned() + page.size());
            List<Long> ids = page.stream().map(ArticleInternalDTO::getArticleId).filter(Objects::nonNull).toList();
            Map<Long, ArticleIndexDoc> docs = esIndexService.mgetArticles(ids);
            for (ArticleInternalDTO article : page) {
                if (article.getArticleId() == null) {
                    continue;
                }
                sourceIds.add(article.getArticleId());
                compareAndPublish(article, docs.get(article.getArticleId()), result);
            }
            ArticleInternalDTO last = page.getLast();
            if (last.getUpdateTime() == null) {
                log.warn("文章 update_time 为空，停止本页推进: articleId={}", last.getArticleId());
                break;
            }
            since = last.getUpdateTime().format(ISO);
            lastId = last.getArticleId();
            if (page.size() < properties.getPageSize()) {
                break;
            }
        }

        if (full) {
            scanOrphans(sourceIds, result);
        }
        return result;
    }

    private void compareAndPublish(ArticleInternalDTO article, ArticleIndexDoc doc, ReconcileResult result) {
        // 非发布态（草稿/下架）不该在索引里；只有真存在文档时才重投，让消费者回查后删掉
        if (article.getState() == null || article.getState() != STATE_PUBLISH) {
            if (doc != null) {
                result.setMismatched(result.getMismatched() + 1);
                publishUpdated(article, result);
            }
            return;
        }
        if (doc == null || !sameArticle(article, doc)) {
            result.setMismatched(result.getMismatched() + 1);
            publishUpdated(article, result);
        }
    }

    private boolean sameArticle(ArticleInternalDTO source, ArticleIndexDoc doc) {
        return sameTime(source.getUpdateTime(), doc.getUpdateTime())
                && Objects.equals(source.getViewCount(), doc.getViewCount())
                && Objects.equals(source.getLikeCount(), doc.getLikeCount())
                && Objects.equals(source.getCommentCount(), doc.getCommentCount())
                && Objects.equals(source.getFavoriteCount(), doc.getFavoriteCount())
                && Objects.equals(source.getState(), doc.getState());
    }

    private boolean sameTime(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.SECONDS).equals(right.truncatedTo(ChronoUnit.SECONDS));
    }

    private void publishUpdated(ArticleInternalDTO article, ReconcileResult result) {
        eventPublisher.send(new ArticleUpdatedEvent(ArticleEventPayload.builder()
                .articleId(article.getArticleId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .state(article.getState())
                .publishTime(article.getPublishTime())
                .updateTime(article.getUpdateTime())
                .build()));
        result.setRepublished(result.getRepublished() + 1);
    }

    /** 全量模式才做：ES 里有、MySQL 未删列表里没有的文档，重投 article.deleted */
    private void scanOrphans(Set<Long> sourceIds, ReconcileResult result) {
        String after = null;
        while (true) {
            List<Long> ids = esIndexService.scanArticleIds(after, properties.getEsScanSize());
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (!sourceIds.contains(id)) {
                    result.setOrphans(result.getOrphans() + 1);
                    eventPublisher.send(new ArticleDeletedEvent(
                            ArticleDeletedPayload.builder().articleId(id).build()));
                    result.setRepublished(result.getRepublished() + 1);
                }
            }
            after = String.valueOf(ids.getLast());
            if (ids.size() < properties.getEsScanSize()) {
                break;
            }
        }
    }
}
