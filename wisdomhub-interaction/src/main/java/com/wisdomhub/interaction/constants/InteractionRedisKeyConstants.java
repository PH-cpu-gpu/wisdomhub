package com.wisdomhub.interaction.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public final class InteractionRedisKeyConstants {

    /** 点赞 / 收藏的防重前置拦截，TTL = RedisTtl.LIKE_ONCE。 */
    public static final String LIKE_ONCE_KEY = RedisPrefix.INTERACTION + "like:once:";

    public static final String FAVORITE_ONCE_KEY = RedisPrefix.INTERACTION + "favorite:once:";

    public static String likeGate(int targetType, Long userId, Long targetId) {
        return LIKE_ONCE_KEY + targetType + ":" + userId + ":" + targetId;
    }

    public static String favoriteGate(int targetType, Long userId, Long targetId) {
        return FAVORITE_ONCE_KEY + targetType + ":" + userId + ":" + targetId;
    }

    private InteractionRedisKeyConstants() {
    }
}
