package com.wisdomhub.common.redis.constants;

import java.time.Duration;

/**
 * 各类 Redis key 的存活时间。写 key 就必须带 TTL，不要留永久 key。
 * 以下均为初值，按实际运行情况调整。
 */
public final class RedisTtl {

    /** access token 有效期 */
    public static final Duration ACCESS_TOKEN = Duration.ofHours(2);

    /** refresh token 有效期 */
    public static final Duration REFRESH_TOKEN = Duration.ofDays(7);

    /** 验证码 */
    public static final Duration CAPTCHA = Duration.ofMinutes(5);

    /** /internal/** Nonce 去重窗口，必须与签名的时间戳窗口一致（技术文档 15.5：±300s） */
    public static final Duration INTERNAL_NONCE = Duration.ofMinutes(5);

    /** 点赞 / 收藏 防重前置拦截 */
    public static final Duration LIKE_ONCE = Duration.ofHours(1);

    /** 热门榜单刷新间隔 */
    public static final Duration HOT_RANK = Duration.ofMinutes(30);

    /** Spring Cache 的默认存活时间，单个 cacheName 可另行覆盖 */
    public static final Duration CACHE_DEFAULT = Duration.ofMinutes(30);

    private RedisTtl() {}
}
