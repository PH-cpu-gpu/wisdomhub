package com.wisdomhub.notification.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

import java.time.Duration;

public final class NotificationRedisConstants {

    /** 消费去重：key 后接 eventId */
    public static final String EVENT_DEDUP_KEY = RedisPrefix.NOTIFICATION + "event:";

    /** 未读数：key 后接 userId，TTL = RedisTtl.NOTIFICATION_UNREAD */
    public static final String UNREAD_COUNT_KEY = RedisPrefix.NOTIFICATION + "unread:count:";

    /** 比死信重投窗口更长；B2.30 重投必须换新 eventId，否则会被这里挡掉 */
    public static final Duration EVENT_DEDUP_TTL = Duration.ofDays(7);

    private NotificationRedisConstants() {
    }
}
