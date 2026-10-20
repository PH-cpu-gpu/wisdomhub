package com.wisdomhub.knowledge.service;

import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleDeletedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.knowledge.api.ArticleBriefFeign;
import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import com.wisdomhub.knowledge.domain.vo.GraphReconcileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Neo4j 对账。只做 Article 节点存在性与孤岛清理 —— ABOUT 边的真值源是 AI 抽取结果（Mongo 的
 * ai_analysis_result，B3.x 才落地），MySQL 里没有这份事实，所以边不重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphReconcileService {

    private static final String LIST_ARTICLES = "MATCH (a:Article) RETURN a.articleId AS articleId";

    private static final String DELETE_NO_EDGE = "MATCH (a:Article) WHERE NOT (a)-[:ABOUT]->() "
            + "WITH a LIMIT $limit DELETE a RETURN count(*) AS deleted";

    private static final int BATCH_SIZE = 100;

    private static final int DELETE_BATCH = 1000;

    private final Neo4jClient neo4jClient;
    private final ArticleBriefFeign articleBriefFeign;
    private final EventPublisher eventPublisher;

    @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    public GraphReconcileResult reconcile() {
        GraphReconcileResult result = new GraphReconcileResult();
        List<Long> articleIds = listArticleIds();
        result.setArticleNodes(articleIds.size());

        Set<Long> existing = new HashSet<>();
        for (int i = 0; i < articleIds.size(); i += BATCH_SIZE) {
            List<Long> batch = articleIds.subList(i, Math.min(i + BATCH_SIZE, articleIds.size()));
            List<Long> found = articleBriefFeign.existingArticleIds(batch);
            if (found != null) {
                existing.addAll(found);
            }
        }

        for (Long articleId : articleIds) {
            if (!existing.contains(articleId)) {
                result.setMissingArticles(result.getMissingArticles() + 1);
                // 重投给 knowledge.article.q，自己的消费者删节点；search/user 也会收到，语义正确
                eventPublisher.send(new ArticleDeletedEvent(
                        ArticleDeletedPayload.builder().articleId(articleId).build()));
            }
        }

        result.setNoEdgeNodes(deleteNoEdgeNodes());
        return result;
    }

    private List<Long> listArticleIds() {
        Collection<Map<String, Object>> rows = neo4jClient.query(LIST_ARTICLES).fetch().all();
        List<Long> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object raw = row.get("articleId");
            if (raw == null) {
                continue;
            }
            try {
                ids.add(Long.valueOf(String.valueOf(raw)));
            } catch (NumberFormatException e) {
                log.warn("图谱 Article 节点的 articleId 不是数字，跳过: {}", raw);
            }
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** 图里 Article 存在的唯一理由就是它有 ABOUT 边；没有边的节点是残留 */
    private int deleteNoEdgeNodes() {
        int total = 0;
        while (true) {
            int deleted = neo4jClient.query(DELETE_NO_EDGE)
                    .bind(DELETE_BATCH).to("limit")
                    .fetch().one()
                    .map(row -> ((Number) row.get("deleted")).intValue())
                    .orElse(0);
            total += deleted;
            if (deleted < DELETE_BATCH) {
                break;
            }
        }
        if (total > 0) {
            log.info("清掉无 ABOUT 边的 Article 节点: {}", total);
        }
        return total;
    }
}
