package com.wisdomhub.knowledge.service;

import com.wisdomhub.knowledge.constants.KnowledgeCacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图谱里文章侧的唯一入口。两条操作都幂等，重复投递/重投结果一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphArticleService {

    private static final String DELETE_ABOUT = "MATCH (a:Article {articleId: $articleId})-[r:ABOUT]->() DELETE r";

    private static final String MERGE_ARTICLE = "MERGE (a:Article {articleId: $articleId})";

    private static final String LINK_ABOUT = "MATCH (a:Article {articleId: $articleId}) "
            + "UNWIND $tagIds AS tagId MATCH (t:Tag {tagId: tagId}) MERGE (a)-[:ABOUT]->(t)";

    private static final String DELETE_ARTICLE = "MATCH (a:Article {articleId: $articleId}) DETACH DELETE a";

    private final Neo4jClient neo4jClient;
    private final TagNameResolver tagNameResolver;
    private final UnknownEntityReporter unknownEntityReporter;

    /**
     * 整篇重建该文章的 ABOUT 边。
     */
    @Transactional
    @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    public void rebuildAbout(Long articleId, Collection<String> names) {
        String id = String.valueOf(articleId);
        Set<String> wanted = names.stream()
                .filter(name -> name != null)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> resolved = tagNameResolver.resolve(wanted);
        Set<String> matchedNames = resolved.keySet();
        List<String> tagIds = resolved.values().stream().distinct().toList();

        if (matchedNames.size() < wanted.size()) {
            Set<String> dropped = new LinkedHashSet<>(wanted);
            dropped.removeAll(matchedNames);
            if (!dropped.isEmpty()) {
                log.warn("抽取的实体不在标签表，丢弃: articleId={} names={}", articleId, dropped);
                // 只存证据，升不升级由 content 的 job 按文章证据判断
                unknownEntityReporter.reportExtract(articleId, dropped);
            }
        }

        neo4jClient.query(DELETE_ABOUT).bind(id).to("articleId").run();
        if (tagIds.isEmpty()) {
            // 一条边都没有的文章节点是噪声：图里 Article 存在的唯一理由就是它有 ABOUT 边
            neo4jClient.query(DELETE_ARTICLE).bind(id).to("articleId").run();
            log.info("没有命中任何标签，文章已清出图谱: articleId={}", articleId);
            return;
        }
        neo4jClient.query(MERGE_ARTICLE).bind(id).to("articleId").run();
        neo4jClient.query(LINK_ABOUT).bind(id).to("articleId").bind(tagIds).to("tagIds").run();
        log.info("ABOUT 已重建: articleId={} edges={}", articleId, tagIds.size());
    }

    /** 文章删除：节点与它的 ABOUT 边一起走 */
    @CacheEvict(cacheNames = KnowledgeCacheConstants.CACHE_GRAPH_NEIGHBORHOOD, allEntries = true)
    public void deleteArticle(Long articleId) {
        neo4jClient.query(DELETE_ARTICLE).bind(String.valueOf(articleId)).to("articleId").run();
        log.info("文章已清出图谱: articleId={}", articleId);
    }
}
