package com.wisdomhub.knowledge.service;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.knowledge.domain.vo.PrerequisiteLevelVO;
import com.wisdomhub.knowledge.domain.vo.PrerequisiteTreeVO;
import com.wisdomhub.knowledge.domain.vo.RelationGroupVO;
import com.wisdomhub.knowledge.domain.vo.TagBriefVO;
import com.wisdomhub.knowledge.domain.vo.TagDetailVO;
import com.wisdomhub.knowledge.domain.vo.TagRelationsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图谱读侧：技术详情、相关技术、前置知识链。只读，不写图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphQueryService {

    /** Tag→Tag 的全部关系类型，白名单写死；新增一种关系要在这里显式登记才会出现在「相关技术」里 */
    private static final List<String> TAG_RELATION_TYPES =
            List.of("BASED_ON", "USES", "PREREQUISITE", "SIMILAR_TO", "BELONGS_TO");

    /** 种子里这条边不是成对的（Kafka→RabbitMQ、RabbitMQ→RocketMQ…），按无向取、两边并进 out */
    private static final String UNDIRECTED_RELATION = "SIMILAR_TO";

    private static final int MAX_DEPTH_LIMIT = 10;

    private static final String FIND_TAG = "MATCH (t:Tag {tagId: $tagId}) "
            + "RETURN t.tagId AS tagId, t.name AS name, t.category AS category, t.description AS description";

    private static final String FIND_NEIGHBORS = "MATCH (t:Tag {tagId: $tagId})-[r]-(n:Tag) WHERE type(r) IN $types "
            + "RETURN type(r) AS rel, startNode(r) = t AS outgoing, n.tagId AS tagId, n.name AS name ORDER BY rel, n.name";

    private static final String FIND_PREREQUISITES = "UNWIND $tagIds AS id "
            + "MATCH (:Tag {tagId: id})-[:PREREQUISITE]->(pre:Tag) "
            + "RETURN DISTINCT pre.tagId AS tagId, pre.name AS name ORDER BY name";

    private final Neo4jClient neo4jClient;

    /** 技术详情 = 节点属性 + 相关技术 */
    public TagDetailVO detail(String tagId) {
        Map<String, Object> tag = findTag(tagId);
        return TagDetailVO.builder()
                .tagId((String) tag.get("tagId"))
                .name((String) tag.get("name"))
                .category((String) tag.get("category"))
                .description((String) tag.get("description"))
                .relations(loadRelations(tagId))
                .build();
    }

    /** 相关技术 = 该技术的全部直接邻居，按关系类型分组 */
    public TagRelationsVO related(String tagId) {
        Map<String, Object> tag = findTag(tagId);
        return TagRelationsVO.builder()
                .tagId((String) tag.get("tagId"))
                .name((String) tag.get("name"))
                .relations(loadRelations(tagId))
                .build();
    }

    /** 前置知识链：沿 PREREQUISITE 出边按层展开，visited 挡环 */
    public PrerequisiteTreeVO prerequisites(String tagId, int maxDepth) {
        if (maxDepth < 1 || maxDepth > MAX_DEPTH_LIMIT) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "maxDepth 需在 1~" + MAX_DEPTH_LIMIT + " 之间");
        }
        Map<String, Object> tag = findTag(tagId);
        String startId = (String) tag.get("tagId");

        List<PrerequisiteLevelVO> levels = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(startId);
        List<String> frontier = List.of(startId);
        boolean truncated = false;

        for (int depth = 1; depth <= maxDepth; depth++) {
            List<TagBriefVO> next = loadPrerequisites(frontier).stream()
                    .filter(pre -> !visited.contains(pre.getTagId()))
                    .toList();
            if (next.isEmpty()) {
                break;
            }
            levels.add(PrerequisiteLevelVO.builder().depth(depth).tags(next).build());
            next.forEach(pre -> visited.add(pre.getTagId()));
            frontier = next.stream().map(TagBriefVO::getTagId).toList();
            if (depth == maxDepth) {
                // 再探一层：还有没展开的前置才是真被截断，否则只是链到头了
                truncated = loadPrerequisites(frontier).stream().anyMatch(pre -> !visited.contains(pre.getTagId()));
            }
        }

        return PrerequisiteTreeVO.builder()
                .tagId(startId)
                .name((String) tag.get("name"))
                .depth(levels.size())
                .levels(levels)
                .truncated(truncated)
                .build();
    }

    /** 定位起点。*/
    private Map<String, Object> findTag(String tagId) {
        return neo4jClient.query(FIND_TAG).bind(tagId).to("tagId")
                .fetch().all().stream().findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "技术不存在: " + tagId));
    }

    private Map<String, RelationGroupVO> loadRelations(String tagId) {
        Map<String, RelationGroupVO> relations = new LinkedHashMap<>();
        TAG_RELATION_TYPES.forEach(type -> relations.put(type, RelationGroupVO.builder()
                .out(new ArrayList<>())
                .in(new ArrayList<>())
                .build()));

        neo4jClient.query(FIND_NEIGHBORS).bind(tagId).to("tagId").bind(TAG_RELATION_TYPES).to("types")
                .fetch().all()
                .forEach(row -> {
                    String rel = (String) row.get("rel");
                    TagBriefVO neighbor = TagBriefVO.builder()
                            .tagId((String) row.get("tagId"))
                            .name((String) row.get("name"))
                            .build();
                    if (Boolean.TRUE.equals(row.get("outgoing")) || UNDIRECTED_RELATION.equals(rel)) {
                        relations.get(rel).getOut().add(neighbor);
                    } else {
                        relations.get(rel).getIn().add(neighbor);
                    }
                });
        return relations;
    }

    private List<TagBriefVO> loadPrerequisites(List<String> tagIds) {
        return neo4jClient.query(FIND_PREREQUISITES).bind(tagIds).to("tagIds")
                .fetch().all().stream()
                .map(row -> TagBriefVO.builder()
                        .tagId((String) row.get("tagId"))
                        .name((String) row.get("name"))
                        .build())
                .toList();
    }
}
