package com.wisdomhub.interaction.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public final class InteractionRedisKeyConstants {

    /** 点赞状态：已赞 / 取消后冷却，TTL 见 RedisTtl。 */
    public static final String LIKE_STATE_KEY = RedisPrefix.INTERACTION + "like:state:";

    public static final String FAVORITE_ONCE_KEY = RedisPrefix.INTERACTION + "favorite:once:";

    /** 计数快照读缓存，TTL = RedisTtl.INTERACTION_COUNT。 */
    public static final String COUNT_KEY = RedisPrefix.INTERACTION + "count:";

    public static String likeState(int targetType, Long userId, Long targetId) {
        return LIKE_STATE_KEY + targetType + ":" + userId + ":" + targetId;
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
