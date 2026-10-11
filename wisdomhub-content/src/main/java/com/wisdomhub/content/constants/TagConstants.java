package com.wisdomhub.content.constants;

public class TagConstants {

    private TagConstants() {}

    /** 全量标签缓存的 cacheName，最终 Redis key 形如 wisdomhub:content:cache:tag::all */
    public static final String CACHE_TAG = "tag";

    /** 热门标签缓存的 cacheName，最终 Redis key 形如 wisdomhub:content:cache:tag:hot::10 */
    public static final String CACHE_HOT_TAG = "tag:hot";

    public static final int HOT_LIMIT_DEFAULT = 10;
    public static final int HOT_LIMIT_MAX = 50;

    /** 未指定分组时的兜底分组名，与 tag.category 的 DDL 默认值一致 */
    public static final String CATEGORY_DEFAULT = "其他";

    /** 必须在进 @Cacheable 之前钳制，否则任意整数会为每个值生成一个缓存 key，撑爆 key 空间 */
    public static int clampHotLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return HOT_LIMIT_DEFAULT;
        }
        return Math.min(limit, HOT_LIMIT_MAX);
    }
}
