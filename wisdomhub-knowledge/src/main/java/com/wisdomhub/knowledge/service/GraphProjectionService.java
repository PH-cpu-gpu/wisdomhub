package com.wisdomhub.knowledge.service;

import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import com.wisdomhub.common.mq.event.payload.TagRelationChangedPayload;
import com.wisdomhub.knowledge.constants.GraphRelationConstants;
import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 把 content 的标签与关系事件投影到 Neo4j。MERGE/DELETE 都幂等，重复投递无副作用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphProjectionService {

    private static final String UPSERT_TAG = "MERGE (t:Tag {tagId: $tagId}) "
            + "SET t.name = $name, t.category = $category, t.description = $description";

    private static final String DELETE_TAG = "MATCH (t:Tag {tagId: $tagId}) DETACH DELETE t";

    private final Neo4jClient neo4jClient;

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public void upsertTag(TagEventPayload payload) {
        if (payload == null || payload.getTagId() == null || payload.getName() == null) {
            throw new IllegalArgumentException("标签事件缺少 tagId 或 name");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("tagId", String.valueOf(payload.getTagId()));
        params.put("name", payload.getName());
        params.put("category", payload.getCategory());
        params.put("description", payload.getDescription());
        neo4jClient.query(UPSERT_TAG).bindAll(params).run();
        log.debug("标签投影更新: tagId={}", payload.getTagId());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public void deleteTag(Long tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("tagId 不能为空");
        }
        neo4jClient.query(DELETE_TAG).bind(String.valueOf(tagId)).to("tagId").run();
        log.debug("标签投影删除: tagId={}", tagId);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public void upsertRelation(TagRelationChangedPayload payload) {
        String type = requireRelationType(payload);
        String cypher = "MATCH (a:Tag {tagId: $fromTagId}) MATCH (b:Tag {tagId: $toTagId}) "
                + "MERGE (a)-[r:" + type + "]->(b) "
                + "SET r.relationId = $relationId, r.source = $source, r.proposalId = $proposalId";
        Map<String, Object> params = new HashMap<>();
        params.put("fromTagId", String.valueOf(payload.getFromTagId()));
        params.put("toTagId", String.valueOf(payload.getToTagId()));
        params.put("relationId", payload.getRelationId() == null ? null : String.valueOf(payload.getRelationId()));
        params.put("source", payload.getSource());
        params.put("proposalId", payload.getProposalId());
        neo4jClient.query(cypher).bindAll(params).run();
        log.debug("关系投影更新: {} {}->{}", type, payload.getFromTagId(), payload.getToTagId());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public void deleteRelation(TagRelationChangedPayload payload) {
        String type = requireRelationType(payload);
        String cypher = "MATCH (a:Tag {tagId: $fromTagId})-[r:" + type + "]->(b:Tag {tagId: $toTagId}) DELETE r";
        Map<String, Object> params = new HashMap<>();
        params.put("fromTagId", String.valueOf(payload.getFromTagId()));
        params.put("toTagId", String.valueOf(payload.getToTagId()));
        neo4jClient.query(cypher).bindAll(params).run();
        log.debug("关系投影删除: {} {}->{}", type, payload.getFromTagId(), payload.getToTagId());
    }

    /** 类型只允许白名单里的值拼进 Cypher，防止事件里夹带语句 */
    private String requireRelationType(TagRelationChangedPayload payload) {
        if (payload == null || payload.getFromTagId() == null || payload.getToTagId() == null
                || !GraphRelationConstants.TYPES.contains(payload.getRelationType())) {
            throw new IllegalArgumentException("关系事件字段不合法");
        }
        return payload.getRelationType();
    }
}
