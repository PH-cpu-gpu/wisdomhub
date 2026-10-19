package com.wisdomhub.knowledge.service;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.knowledge.api.ArticleBriefFeign;
import com.wisdomhub.knowledge.api.dto.ArticleBriefDTO;
import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import com.wisdomhub.knowledge.domain.vo.GraphEdgeVO;
import com.wisdomhub.knowledge.domain.vo.GraphNodeVO;
import com.wisdomhub.knowledge.domain.vo.GraphVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把图谱整块地交给前端画。总览给技术全景，邻域给「以某技术为中心 + 讲它的文章」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphVisualizationService {

    private static final String TAG_RELATIONS = "BASED_ON|USES|PREREQUISITE|SIMILAR_TO|BELONGS_TO";

    private static final int MAX_DEPTH = 3;
    private static final int MAX_ARTICLE_LIMIT = 50;

    private static final String ALL_TAG_NODES = "MATCH (t:Tag) "
            + "RETURN t.tagId AS id, t.name AS label, t.category AS category, COUNT { (t)--(:Tag) } AS degree";

    private static final String ALL_TAG_EDGES = "MATCH (a:Tag)-[r]->(b:Tag) "
            + "RETURN a.tagId AS source, b.tagId AS target, type(r) AS relation";

    private static final String FIND_TAG = "MATCH (t:Tag {tagId: $tagId}) "
            + "RETURN t.tagId AS id, t.name AS label, t.category AS category, COUNT { (t)--(:Tag) } AS degree";

    private static final String INDUCED_EDGES = "MATCH (a:Tag)-[r]->(b:Tag) "
            + "WHERE a.tagId IN $ids AND b.tagId IN $ids "
            + "RETURN a.tagId AS source, b.tagId AS target, type(r) AS relation";

    private static final String CENTER_ARTICLES = "MATCH (a:Article)-[:ABOUT]->(t:Tag {tagId: $tagId}) "
            + "RETURN a.articleId AS id ORDER BY a.articleId DESC LIMIT $limit";

    private final Neo4jClient neo4jClient;
    private final ArticleBriefFeign articleBriefFeign;

    /** 总览：整张技术图。Tag 是人工维护的话题级词表（130 条封顶），文章数无界所以不进这里 */
    @Cacheable(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH, key = "'all'")
    public GraphVO overview() {
        // 缓存值不能含 JDK 不可变集合，否则 Redis 读回失败
        List<GraphNodeVO> nodes = new ArrayList<>(neo4jClient.query(ALL_TAG_NODES).fetch().all().stream()
                .map(GraphVisualizationService::toTagNode)
                .toList());
        List<GraphEdgeVO> edges = new ArrayList<>(mapEdges(neo4jClient.query(ALL_TAG_EDGES).fetch().all()));
        return GraphVO.builder().nodes(nodes).edges(edges).articlesTruncated(false).build();
    }

    /** 邻域：中心 + depth 跳内的 Tag + 它们之间的边 + 只挂中心的文章 */
    @Cacheable(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD,
            key = "#tagId + ':' + #depth + ':' + #articleLimit")
    public GraphVO neighborhood(String tagId, int depth, int articleLimit) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "depth 需在 1~" + MAX_DEPTH + " 之间");
        }
        if (articleLimit < 1 || articleLimit > MAX_ARTICLE_LIMIT) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "articleLimit 需在 1~" + MAX_ARTICLE_LIMIT + " 之间");
        }

        List<GraphNodeVO> nodes = new ArrayList<>();
        nodes.add(toTagNode(findTag(tagId)));
        neo4jClient.query(neighborTags(depth)).bind(tagId).to("tagId").fetch().all().stream()
                .map(GraphVisualizationService::toTagNode)
                .forEach(nodes::add);

        List<String> tagIds = nodes.stream().map(GraphNodeVO::getId).toList();
        List<GraphEdgeVO> edges = new ArrayList<>(mapEdges(
                neo4jClient.query(INDUCED_EDGES).bind(tagIds).to("ids").fetch().all()));

        List<String> articleIds = loadCenterArticleIds(tagId, articleLimit);
        boolean truncated = articleIds.size() > articleLimit;
        List<String> shown = truncated ? articleIds.subList(0, articleLimit) : articleIds;
        Map<String, String> titles = loadTitles(shown);

        for (String articleId : shown) {
            nodes.add(GraphNodeVO.builder().id(articleId).type("ARTICLE").label(titles.get(articleId)).build());
            edges.add(GraphEdgeVO.builder().source(articleId).target(tagId).relation("ABOUT").build());
        }
        return GraphVO.builder().nodes(nodes).edges(edges).articlesTruncated(truncated).build();
    }

    /** 变长路径的长度不能参数化，只能拼；depth 已校验在 1~3 */
    private static String neighborTags(int depth) {
        return "MATCH (c:Tag {tagId: $tagId}) MATCH (c)-[:" + TAG_RELATIONS + "*1.." + depth + "]-(x:Tag) "
                + "WHERE x <> c RETURN DISTINCT x.tagId AS id, x.name AS label, x.category AS category, "
                + "COUNT { (x)--(:Tag) } AS degree";
    }

    private Map<String, Object> findTag(String tagId) {
        return neo4jClient.query(FIND_TAG).bind(tagId).to("tagId")
                .fetch().all().stream().findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "技术不存在: " + tagId));
    }

    /** 多取一行判截断，省一次 COUNT */
    private List<String> loadCenterArticleIds(String tagId, int articleLimit) {
        return neo4jClient.query(CENTER_ARTICLES).bind(tagId).to("tagId").bind(articleLimit + 1).to("limit")
                .fetch().all().stream()
                .map(row -> (String) row.get("id"))
                .toList();
    }

    /** 标题只从 content-service 要：图上不存 title，单一事实来源是 MySQL；补不到就留 null，不让图谱页白屏 */
    private Map<String, String> loadTitles(List<String> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Long> ids = articleIds.stream().map(Long::valueOf).toList();
            Map<String, String> titles = new HashMap<>();
            for (ArticleBriefDTO brief : articleBriefFeign.listBrief(ids)) {
                titles.put(String.valueOf(brief.getArticleId()), brief.getTitle());
            }
            return titles;
        } catch (Exception e) {
            log.warn("补文章标题失败，本次文章节点不带标题: articleIds={}", articleIds, e);
            return Map.of();
        }
    }

    private static GraphNodeVO toTagNode(Map<String, Object> row) {
        return GraphNodeVO.builder()
                .id((String) row.get("id"))
                .type("TAG")
                .label((String) row.get("label"))
                .category((String) row.get("category"))
                .degree(((Number) row.get("degree")).intValue())
                .build();
    }

    private static List<GraphEdgeVO> mapEdges(Collection<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> GraphEdgeVO.builder()
                        .source((String) row.get("source"))
                        .target((String) row.get("target"))
                        .relation((String) row.get("relation"))
                        .build())
                .toList();
    }
}
