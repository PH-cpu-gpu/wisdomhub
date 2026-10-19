package com.wisdomhub.knowledge.constants;

public class KnowledgeCacheConstants {

    private KnowledgeCacheConstants() {}

    /** 最终 Redis key 形如 wisdomhub:knowledge:cache:tag-detail::1 */
    public static final String CACHE_TAG_DETAIL = "tag-detail";

    /** 公开 /related 与内部 ?tech= 共用，?tech= 的 key 加 tech: 前缀 */
    public static final String CACHE_TAG_RELATED = "tag-related";

    public static final String CACHE_TAG_PREREQUISITES = "tag-prerequisites";

    /** 图谱总览：整张技术图，不含文章 */
    public static final String CACHE_GRAPH = "graph";

    /** 图谱邻域：含文章节点与标题，文章事件后必须清空 */
    public static final String CACHE_GRAPH_NEIGHBORHOOD = "graph:neighborhood";
}
