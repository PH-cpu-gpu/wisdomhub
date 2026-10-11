package com.wisdomhub.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动时按 resources/es/*.json 建索引。已存在就跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer implements ApplicationRunner {

    /**
     * 索引名 → mapping 资源。
     */
    private static final Map<String, String> INDEX_RESOURCES = buildIndexResources();

    private final ElasticsearchClient elasticsearchClient;

    private static Map<String, String> buildIndexResources() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("article_index", "es/article_index.json");
        map.put("question_index", "es/question_index.json");
        map.put("user_index", "es/user_index.json");
        map.put("tag_index", "es/tag_index.json");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public void run(ApplicationArguments args) {
        INDEX_RESOURCES.forEach(this::ensureIndex);
    }

    /**
     * ES 不可达时只记日志不抛：本服务没有别的功能，起不来会连带把整条链路卡住。
     */
    private void ensureIndex(String indexName, String resource) {
        try {
            if (elasticsearchClient.indices().exists(e -> e.index(indexName)).value()) {
                log.info("ES 索引已存在，跳过创建: {}", indexName);
                return;
            }
            ClassPathResource mapping = new ClassPathResource(resource);
            try (InputStream in = mapping.getInputStream()) {
                elasticsearchClient.indices().create(c -> c.index(indexName).withJson(in));
            }
            log.info("ES 索引已创建: {}", indexName);
        } catch (Exception e) {
            log.error("ES 索引初始化失败（检查 ES 是否可达、IK 插件是否加载）: {}", indexName, e);
        }
    }
}
