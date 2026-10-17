package com.wisdomhub.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.notification.constants.NotificationRedisConstants;
import com.wisdomhub.notification.domain.entity.Notification;
import com.wisdomhub.notification.mapper.NotificationMapper;
import com.wisdomhub.notification.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountServiceImpl implements UnreadCountService {

    /** key 不存在时返回 -1：绝不能凭空 INCR 出「1」，否则会把历史未读抹掉 */
    private static final DefaultRedisScript<Long> INCR_IF_EXISTS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('INCR', KEYS[1])
            end
            return -1
            """, Long.class);

    /** 只减到 0，且 key 不存在时跳过；同样避免在重建前的空窗口里制造负数 */
    private static final DefaultRedisScript<Long> DECR_IF_EXISTS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            local value = redis.call('DECR', KEYS[1])
            if value < 0 then
                redis.call('SET', KEYS[1], 0)
                return 0
            end
            return value
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationMapper notificationMapper;

    @Override
    public int get(Long userId) {
        String key = key(userId);
        Integer cached = readCached(key, userId);
        if (cached != null) {
            return cached;
        }
        int count = dbCount(userId);
        try {
            // SETNX：并发重建时只有一个能写成功，后来的直接读回已有值，不互相覆盖
            Boolean initialized = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(count), RedisTtl.NOTIFICATION_UNREAD);
            if (Boolean.TRUE.equals(initialized)) {
                return count;
            }
            Integer winner = readCached(key, userId);
            return winner == null ? count : winner;
        } catch (Exception e) {
            log.warn("未读数回填失败, 本次直接返回 DB 值: userId={}", userId, e);
            return count;
        }
    }

    @Override
    public void increment(Long userId) {
        executeQuietly(INCR_IF_EXISTS, userId, "INCR");
    }

    @Override
    public void decrement(Long userId) {
        executeQuietly(DECR_IF_EXISTS, userId, "DECR");
    }

    @Override
    public void evict(Long userId) {
        try {
            stringRedisTemplate.delete(key(userId));
        } catch (Exception e) {
            log.warn("未读数缓存删除失败, 等 TTL 重建: userId={}", userId, e);
        }
    }

    /**
     * 计数器失败绝不能抛给消费者
     */
    private void executeQuietly(DefaultRedisScript<Long> script, Long userId, String action) {
        try {
            stringRedisTemplate.execute(script, List.of(key(userId)));
        } catch (Exception e) {
            log.warn("未读数 {} 失败, 删除缓存等下次读重建: userId={}", action, userId, e);
            evict(userId);
        }
    }

    /** Redis 不可用时返回 null，由调用方回退 DB；值损坏时清掉 key 让本次重建 */
    private Integer readCached(String key, Long userId) {
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("未读数缓存不可用, 回退 DB: userId={}", userId, e);
            evict(userId);
            return null;
        }
        if (cached == null) {
            return null;
        }
        try {
            return Integer.parseInt(cached);
        } catch (NumberFormatException e) {
            log.warn("未读数缓存不是整数, 清掉后按 DB 重建: userId={}", userId, e);
            evict(userId);
            return null;
        }
    }

    private int dbCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, (byte) 0));
        return count == null ? 0 : Math.toIntExact(count);
    }

    private String key(Long userId) {
        return NotificationRedisConstants.UNREAD_COUNT_KEY + userId;
    }
}
