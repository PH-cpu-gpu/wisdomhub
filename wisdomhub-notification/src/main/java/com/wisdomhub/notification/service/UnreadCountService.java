package com.wisdomhub.notification.service;

/**
 * 未读数缓存。DB 是权威源，Redis 只是读缓存 + 快速计数。
 */
public interface UnreadCountService {

    int get(Long userId);

    /** 落库成功后调用；key 不存在时不做任何事，留给下一次读重建 */
    void increment(Long userId);

    void decrement(Long userId);

    void evict(Long userId);
}
