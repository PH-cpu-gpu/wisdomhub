package com.wisdomhub.content.util;

import com.wisdomhub.content.domain.entity.TagRelation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纯图校验：只判断新增 from→to 后是否会让同类关系成环，不碰数据库。
 */
public final class TagRelationValidator {

    private TagRelationValidator() {
    }

    /** fromTagId 到 toTagId 加边后是否成环 = 现有边里 toTagId 能否走回 fromTagId */
    public static boolean createsCycle(Collection<TagRelation> existing, Long fromTagId, Long toTagId) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (TagRelation relation : existing) {
            adjacency.computeIfAbsent(relation.getFromTagId(), key -> new ArrayList<>()).add(relation.getToTagId());
        }
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        stack.push(toTagId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(fromTagId)) {
                return true;
            }
            for (Long next : adjacency.getOrDefault(current, List.of())) {
                stack.push(next);
            }
        }
        return false;
    }
}
