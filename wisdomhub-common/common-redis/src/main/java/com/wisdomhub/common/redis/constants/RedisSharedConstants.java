package com.wisdomhub.common.redis.constants;

/** 跨服务共用的RedisKey*/
public final class RedisSharedConstants {

    public static final String BLACKLIST_KEY = RedisPrefix.USER + "blacklist:";

    private RedisSharedConstants() {}
}
