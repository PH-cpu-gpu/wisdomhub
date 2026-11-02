package com.wisdomhub.knowledge.service;

import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleDeletedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.knowledge.api.ArticleBriefFeign;
import com.wisdomhub.knowledge.api.ContentTagFeign;
import com.wisdomhub.knowledge.api.dto.TagInternalDTO;
import com.wisdomhub.knowledge.api.dto.TagRelationInternalDTO;
import com.wisdomhub.knowledge.constants.GraphRelationConstants;
import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import com.wisdomhub.knowledge.domain.vo.GraphReconcileResult;
import com.wisdomhub.knowledge.domain.vo.SeedResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.summary.ResultSummary;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Neo4j 对账。标签与关系以 content 的 MySQL 为准：先 re-seed 补齐，再删图里多出来的节点与边；
 * 文章侧只做存在性与孤岛清理 —— ABOUT 边的真值源是 AI 抽取结果，MySQL 里没有这份事实，所以边不重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphReconcileService {

    private static final String LIST_ARTICLES = "MATCH (a:Article) RETURN a.articleId AS articleId";

    private static final String DELETE_NO_EDGE = "MATCH (a:Article) WHERE NOT (a)-[:ABOUT]->() "
            + "WITH a LIMIT $limit DELETE a RETURN count(*) AS deleted";

    private static final String LIST_EDGES = "MATCH (a:Tag)-[r]->(b:Tag) WHERE type(r) IN $types "
            + "RETURN type(r) AS type, a.tagId AS fromTagId, b.tagId AS toTagId";

    private static final String DELETE_STALE_TAGS = "MATCH (t:Tag) "
            + "WHERE t.tagId IS NULL OR NOT (t.tagId IN $tagIds) DETACH DELETE t";

    private static final int BATCH_SIZE = 100;

    private static final int DELETE_BATCH = 1000;

    private final Neo4jClient neo4jClient;
    private final ArticleBriefFeign articleBriefFeign;
    private final ContentTagFeign contentTagFeign;
    private final GraphSeedService graphSeedService;
    private final EventPublisher eventPublisher;

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public GraphReconcileResult reconcile() {
        GraphReconcileResult result = new GraphReconcileResult();

        // 先按 MySQL 补齐节点与边，再删图里多出来的部分；清空 Neo4j 后跑一次即可全量恢复
        SeedResultVO seed = graphSeedService.seed();
        result.setTagNodes(seed.getTagsLoaded());
        result.setStaleRelations(deleteStaleRelations(contentTagFeign.listAllRelations()));
        result.setStaleTags(deleteStaleTags(contentTagFeign.listAllTags()));

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

    private int deleteStaleRelations(List<TagRelationInternalDTO> relations) {
        Set<String> expected = new HashSet<>();
        for (TagRelationInternalDTO relation : relations) {
            expected.add(relationKey(relation.getRelationType(), relation.getFromTagId(), relation.getToTagId()));
        }
        Map<String, List<Map<String, Object>>> staleByType = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(LIST_EDGES)
                .bind(GraphRelationConstants.TYPES).to("types").fetch().all()) {
            String type = (String) row.get("type");
            Object fromTagId = row.get("fromTagId");
            Object toTagId = row.get("toTagId");
            if (fromTagId == null || toTagId == null || !GraphRelationConstants.TYPES.contains(type)) {
                continue;
            }
            String from = String.valueOf(fromTagId);
            String to = String.valueOf(toTagId);
            if (expected.contains(relationKey(type, from, to))) {
                continue;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("fromTagId", from);
            params.put("toTagId", to);
            staleByType.computeIfAbsent(type, key -> new ArrayList<>()).add(params);
        }
        int deleted = 0;
        for (Map.Entry<String, List<Map<String, Object>>> entry : staleByType.entrySet()) {
            String cypher = "UNWIND $rows AS row MATCH (a:Tag {tagId: row.fromTagId})-[r:" + entry.getKey()
                    + "]->(b:Tag {tagId: row.toTagId}) DELETE r";
            ResultSummary summary = neo4jClient.query(cypher).bindAll(Map.of("rows", entry.getValue())).run();
            deleted += summary.counters().relationshipsDeleted();
        }
        if (deleted > 0) {
            log.info("清掉 MySQL 已不存在的图谱关系: {}", deleted);
        }
        return deleted;
    }

    private int deleteStaleTags(List<TagInternalDTO> tags) {
        if (tags.isEmpty()) {
            // 空表更可能是上游异常；宁可不清，也不要一把删光整张图
            log.warn("标签表为空，跳过 Tag 节点清理");
            return 0;
        }
        Set<String> tagIds = tags.stream()
                .map(TagInternalDTO::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        ResultSummary summary = neo4jClient.query(DELETE_STALE_TAGS).bind(tagIds).to("tagIds").run();
        int deleted = summary.counters().nodesDeleted();
        if (deleted > 0) {
            log.info("清掉 MySQL 已不存在的图谱 Tag 节点: {}", deleted);
        }
        return deleted;
    }

    private static String relationKey(String type, String fromTagId, String toTagId) {
        return type + "|" + fromTagId + "|" + toTagId;
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
