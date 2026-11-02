package com.wisdomhub.knowledge.service;

import com.wisdomhub.knowledge.api.ContentTagFeign;
import com.wisdomhub.knowledge.api.dto.TagInternalDTO;
import com.wisdomhub.knowledge.api.dto.TagRelationInternalDTO;
import com.wisdomhub.knowledge.constants.GraphRelationConstants;
import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import com.wisdomhub.knowledge.domain.vo.SeedResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图谱种子导入。节点与关系都来自 content-service 的 MySQL；幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSeedService {

    private final ContentTagFeign contentTagFeign;
    private final Neo4jClient neo4jClient;

    @Caching(evict = {
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_RELATED, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_TAG_PREREQUISITES, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, allEntries = true),
            @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    })
    public SeedResultVO seed() {
        List<TagInternalDTO> tags = contentTagFeign.listAllTags();
        seedNodes(tags);

        List<TagRelationInternalDTO> relations = contentTagFeign.listAllRelations();
        int seeded = 0;
        for (String type : GraphRelationConstants.TYPES) {
            List<TagRelationInternalDTO> rows = relations.stream()
                    .filter(relation -> type.equals(relation.getRelationType()))
                    .toList();
            seedRelations(type, rows);
            seeded += rows.size();
        }
        int dropped = (int) relations.stream()
                .filter(relation -> !GraphRelationConstants.TYPES.contains(relation.getRelationType()))
                .count();
        if (dropped > 0) {
            log.warn("关系类型不在白名单，未建图: {} 条", dropped);
        }
        log.info("图谱种子导入完成: 标签 {} 个、边 {} 条、丢弃 {} 条", tags.size(), seeded, dropped);
        return new SeedResultVO(tags.size(), seeded, dropped);
    }

    private void seedNodes(List<TagInternalDTO> tags) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TagInternalDTO tag : tags) {
            Map<String, Object> row = new HashMap<>();
            row.put("tagId", tag.getTagId());
            row.put("name", tag.getName());
            row.put("category", tag.getCategory());
            row.put("description", tag.getDescription());
            rows.add(row);
        }
        String cypher = "UNWIND $rows AS row MERGE (t:Tag {tagId: row.tagId}) "
                + "SET t.name = row.name, t.category = row.category, t.description = row.description";
        neo4jClient.query(cypher).bindAll(Map.of("rows", rows)).run();
    }

    private void seedRelations(String type, List<TagRelationInternalDTO> relations) {
        if (relations.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>(relations.size());
        for (TagRelationInternalDTO relation : relations) {
            Map<String, Object> row = new HashMap<>();
            row.put("relationId", relation.getRelationId());
            row.put("fromTagId", relation.getFromTagId());
            row.put("toTagId", relation.getToTagId());
            row.put("source", relation.getSource());
            row.put("proposalId", relation.getProposalId());
            rows.add(row);
        }
        String cypher = "UNWIND $rows AS row MATCH (a:Tag {tagId: row.fromTagId}) MATCH (b:Tag {tagId: row.toTagId}) "
                + "MERGE (a)-[r:" + type + "]->(b) "
                + "SET r.relationId = row.relationId, r.source = row.source, r.proposalId = row.proposalId";
        neo4jClient.query(cypher).bindAll(Map.of("rows", rows)).run();
    }
}
