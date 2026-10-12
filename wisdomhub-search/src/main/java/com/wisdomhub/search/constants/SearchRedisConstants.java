package com.wisdomhub.search.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

/**
 * search-service 自己的 Redis key 业务段。
 */
public final class SearchRedisConstants {

    /** 搜索热词：按日一个 zset，日期是变量段所以放最后 */
    public static final String HOT_WORD_DAILY_ZSET = RedisPrefix.SEARCH + "hot:zset:";

    /** 最近 N 天合并出的临时榜单，N 是变量段；刻意不落在 hot:zset: 下，免得被当成日 key 扫出来 */
    public static final String HOT_WORD_UNION_ZSET = RedisPrefix.SEARCH + "hot:union:zset:";

    private SearchRedisConstants() {}
}
