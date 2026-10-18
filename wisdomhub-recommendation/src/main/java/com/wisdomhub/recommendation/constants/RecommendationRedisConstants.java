package com.wisdomhub.recommendation.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

import java.time.Duration;

public final class RecommendationRedisConstants {

    /** 标签画像按月分桶，key 后接 userId:yyyyMM */
    public static final String PROFILE_TAG_KEY = RedisPrefix.RECOMMENDATION + "profile:tag:zset:";

    /** 消费去重，key 后接 eventId */
    public static final String BEHAVIOR_DEDUP_KEY = RedisPrefix.RECOMMENDATION + "behavior:dedup:";

    /** 画像桶保留 100 天，够读最近 3 个月 */
    public static final Duration PROFILE_BUCKET_TTL = Duration.ofDays(100);

    /** 与通知落库对齐：长于死信重投窗口 */
    public static final Duration BEHAVIOR_DEDUP_TTL = Duration.ofDays(7);

    private RecommendationRedisConstants() {
    }
}
