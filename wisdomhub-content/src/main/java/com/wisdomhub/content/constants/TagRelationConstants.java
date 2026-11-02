package com.wisdomhub.content.constants;

import java.util.Set;

public final class TagRelationConstants {

    public static final String BASED_ON = "BASED_ON";
    public static final String USES = "USES";
    public static final String PREREQUISITE = "PREREQUISITE";
    public static final String SIMILAR_TO = "SIMILAR_TO";
    public static final String BELONGS_TO = "BELONGS_TO";

    public static final Set<String> TYPES = Set.of(BASED_ON, USES, PREREQUISITE, SIMILAR_TO, BELONGS_TO);

    /** 方向性语义必须无环；USES 与 SIMILAR_TO 允许互相指向 */
    public static final Set<String> ACYCLIC_TYPES = Set.of(BASED_ON, PREREQUISITE, BELONGS_TO);

    public static final int SOURCE_SEED = 1;
    public static final int SOURCE_ADMIN = 2;
    public static final int SOURCE_AI = 3;

    private TagRelationConstants() {
    }
}
