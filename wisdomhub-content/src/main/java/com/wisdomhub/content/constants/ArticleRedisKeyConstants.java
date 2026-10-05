package com.wisdomhub.content.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public class ArticleRedisKeyConstants {

    private ArticleRedisKeyConstants() {}

    /** 浏览量计数。它是阅读量的读源，**故意不设 TTL**，由 B1.22 负责落库 */
    public static final String VIEW_COUNT_KEY = RedisPrefix.CONTENT + "view:count:";

    /** 阅读防重，TTL = RedisTtl.VIEW_ONCE。变量段在后，可 SCAN view:once:*:{articleId} 清理 */
    public static final String VIEW_ONCE_KEY = RedisPrefix.CONTENT + "view:once:";
}
