package com.wisdomhub.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.knowledge.api.ContentTagFeign;
import com.wisdomhub.knowledge.api.dto.TagInternalDTO;
import com.wisdomhub.knowledge.domain.vo.SeedResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 图谱种子导入。节点来自 content-service 的 tag 表，关系来自 resources/neo4j/seed-relations.json。幂等 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSeedService {

    private static final String RELATIONS_RESOURCE = "neo4j/seed-relations.json";
    private static final Set<String> RELATION_TYPES =
            Set.of("BASED_ON", "USES", "PREREQUISITE", "SIMILAR_TO", "BELONGS_TO");

    private final ContentTagFeign contentTagFeign;
    private final Driver driver;
    private final ObjectMapper objectMapper;

    public SeedResultVO seed() {
        List<TagInternalDTO> tags = contentTagFeign.listAllTags();
        seedNodes(tags);

        Map<String, String> nameToId = indexByName(tags);
        Map<String, List<List<String>>> relations = loadRelations();

        int seeded = 0;
        int dropped = 0;
        for (String type : RELATION_TYPES) {
            List<List<String>> pairs = relations.get(type);
            if (pairs == null) {
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            for (List<String> pair : pairs) {
                String from = nameToId.get(pair.get(0));
                String to = nameToId.get(pair.get(1));
                if (from == null || to == null) {
                    dropped++;
                    log.warn("种子关系端点不在标签表，丢弃: {} {}", type, pair);
                    continue;
                }
                rows.add(List.of(from, to));
            }
            seedRelations(type, rows);
            seeded += rows.size();
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
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(cypher, Map.of("rows", rows)).consume());
        }
    }

    private void seedRelations(String type, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String cypher = "UNWIND $rows AS row MATCH (a:Tag {tagId: row[0]}) MATCH (b:Tag {tagId: row[1]}) "
                + "MERGE (a)-[:" + type + "]->(b)";
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(cypher, Map.of("rows", rows)).consume());
        }
    }

    private Map<String, String> indexByName(List<TagInternalDTO> tags) {
        Map<String, String> map = new HashMap<>();
        for (TagInternalDTO tag : tags) {
            map.put(tag.getName(), tag.getTagId());
        }
        return map;
    }

    private Map<String, List<List<String>>> loadRelations() {
        try (InputStream in = new ClassPathResource(RELATIONS_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<Map<String, List<List<String>>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取图谱种子关系文件失败: " + RELATIONS_RESOURCE, e);
        }
    }
}
