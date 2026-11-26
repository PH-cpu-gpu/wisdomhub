package com.wisdomhub.interaction.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public final class InteractionRedisKeyConstants {

    /** 点赞 / 收藏的防重前置拦截，TTL = RedisTtl.LIKE_ONCE。 */
    public static final String LIKE_ONCE_KEY = RedisPrefix.INTERACTION + "like:once:";

    public static final String FAVORITE_ONCE_KEY = RedisPrefix.INTERACTION + "favorite:once:";

    /** 取消点赞后的再赞冷却，TTL = RedisTtl.LIKE_COOLDOWN。 */
    public static final String LIKE_COOLDOWN_KEY = RedisPrefix.INTERACTION + "like:cooldown:";

    /** 计数快照读缓存，TTL = RedisTtl.INTERACTION_COUNT。 */
    public static final String COUNT_KEY = RedisPrefix.INTERACTION + "count:";

    public static String likeGate(int targetType, Long userId, Long targetId) {
        return LIKE_ONCE_KEY + targetType + ":" + userId + ":" + targetId;
    }

    public static String likeCooldown(int targetType, Long userId, Long targetId) {
        return LIKE_COOLDOWN_KEY + targetType + ":" + userId + ":" + targetId;
    }

    public static String favoriteGate(int targetType, Long userId, Long targetId) {
        return FAVORITE_ONCE_KEY + targetType + ":" + userId + ":" + targetId;
    }

    public static String count(int targetType, Long targetId) {
        return COUNT_KEY + targetType + ":" + targetId;
    }

    private InteractionRedisKeyConstants() {
    }
}
