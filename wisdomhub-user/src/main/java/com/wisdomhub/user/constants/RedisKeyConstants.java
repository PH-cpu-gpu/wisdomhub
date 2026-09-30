package com.wisdomhub.user.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public class RedisKeyConstants {

    private static final String CODE_KEY = RedisPrefix.USER + "code:";
    public static final String CODE_BY_PHONE_KEY = CODE_KEY + "phone:";
    public static final String CODE_BY_EMAIL_KEY = CODE_KEY + "email:";

    public static final String REFRESH_TOKEN_KEY = RedisPrefix.USER + "refresh:";
}
