package com.wisdomhub.video.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public class VideoRedisKeyConstants {

    private VideoRedisKeyConstants() {
    }

    /** 播放量计数，读源；不设 TTL，由 XXL 任务落库 */
    public static final String VIEW_COUNT_KEY = RedisPrefix.VIDEO + "view:count:";

    /** 播放防重，TTL = RedisTtl.VIEW_ONCE */
    public static final String VIEW_ONCE_KEY = RedisPrefix.VIDEO + "view:once:";
}
