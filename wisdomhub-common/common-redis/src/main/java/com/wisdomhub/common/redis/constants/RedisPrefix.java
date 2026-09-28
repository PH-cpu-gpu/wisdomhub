package com.wisdomhub.common.redis.constants;

/**
 * Redis key 统一前缀：{@code wisdomhub:{服务}:{业务}:{标识}}。
 * 这里只放全局前缀与服务段；各服务自己的业务段由该服务声明，不往 common 层堆。
 */
public final class RedisPrefix {

    /** 全项目统一前缀，任何 key 都必须以它开头 */
    public static final String APP = "wisdomhub";

    public static final String USER = APP + ":user";
    public static final String CONTENT = APP + ":content";
    public static final String SEARCH = APP + ":search";
    public static final String KNOWLEDGE = APP + ":knowledge";
    public static final String RECOMMENDATION = APP + ":recommendation";
    public static final String NOTIFICATION = APP + ":notification";
    public static final String ORDER = APP + ":order";
    public static final String AGENT = APP + ":agent";
    public static final String INTERNAL = APP + ":internal";

    private RedisPrefix() {
    }
}
