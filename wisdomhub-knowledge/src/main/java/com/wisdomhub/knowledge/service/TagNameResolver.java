package com.wisdomhub.knowledge.service;

import com.wisdomhub.knowledge.api.ContentTagFeign;
import com.wisdomhub.knowledge.api.dto.TagResolveDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实体名 → tagId。先查图里的精确名（不走远程），剩下的交给 content 做归一化与别名解析。
 */
@Component
@RequiredArgsConstructor
public class TagNameResolver {

    private static final String MATCH_EXACT = "UNWIND $names AS name "
            + "MATCH (t:Tag {name: name}) RETURN name, t.tagId AS tagId";

    private final Neo4jClient neo4jClient;
    private final ContentTagFeign contentTagFeign;

    public Map<String, String> resolve(Collection<String> names) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) {
            return resolved;
        }
        List<String> pending = names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (pending.isEmpty()) {
            return resolved;
        }
        for (Map<String, Object> row : neo4jClient.query(MATCH_EXACT).bind(pending).to("names").fetch().all()) {
            Object tagId = row.get("tagId");
            if (tagId != null) {
                resolved.put((String) row.get("name"), String.valueOf(tagId));
            }
        }
        List<String> unresolved = pending.stream().filter(name -> !resolved.containsKey(name)).toList();
        if (!unresolved.isEmpty()) {
            for (TagResolveDTO dto : contentTagFeign.resolveTags(unresolved)) {
                if (dto != null && dto.getName() != null && dto.getTagId() != null) {
                    resolved.putIfAbsent(dto.getName(), dto.getTagId());
                }
            }
        }
        return resolved;
    }

    public String resolveOne(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        return resolve(List.of(trimmed)).get(trimmed);
    }
}
