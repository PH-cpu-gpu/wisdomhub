package com.wisdomhub.common.redis.constants;

/** 跨服务共用的RedisKey*/
public final class RedisSharedConstants {

    public static final String BLACKLIST_KEY = RedisPrefix.USER + "blacklist:";

    /** 会话踢线广播频道：user 服务登出后发布，notification 各实例关闭本机会话 */
    public static final String SESSION_KICK_CHANNEL = RedisPrefix.USER + "session:kick";

    private RedisSharedConstants() {}
}
