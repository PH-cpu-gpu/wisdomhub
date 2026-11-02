package com.wisdomhub.knowledge.constants;

import java.util.Set;

public final class GraphRelationConstants {

    public static final String BASED_ON = "BASED_ON";
    public static final String USES = "USES";
    public static final String PREREQUISITE = "PREREQUISITE";
    public static final String SIMILAR_TO = "SIMILAR_TO";
    public static final String BELONGS_TO = "BELONGS_TO";

    /** Neo4j 边的类型白名单；只允许这几种拼进 Cypher，防止事件里的类型注入 */
    public static final Set<String> TYPES = Set.of(BASED_ON, USES, PREREQUISITE, SIMILAR_TO, BELONGS_TO);

    private GraphRelationConstants() {
    }
}
