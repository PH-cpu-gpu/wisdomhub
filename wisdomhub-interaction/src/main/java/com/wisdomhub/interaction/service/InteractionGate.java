package com.wisdomhub.interaction.service;

import com.wisdomhub.common.redis.constants.RedisTtl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 点赞/收藏的 Redis 防重快路径；Redis 不可用时放行，正确性由唯一键兜底。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionGate {

    private static final String LIKED = "liked";
    private static final String COOLING = "cooling";

    private final StringRedisTemplate stringRedisTemplate;

    /** 点赞状态：ABSENT 可赞、LIKED 已赞、COOLING 取消后冷却中。 */
    public enum LikeState { ABSENT, LIKED, COOLING }

    /** Redis 异常按 ABSENT 放行，正确性仍由唯一索引兜底。 */
    public LikeState likeState(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return LikeState.ABSENT;
            }
            return COOLING.equals(value) ? LikeState.COOLING : LikeState.LIKED;
        } catch (Exception e) {
            log.warn("点赞状态读取不可用, 放行由唯一索引兜底: key={}", key, e);
            return LikeState.ABSENT;
        }
    }

    /** 仅当无状态时原子置为已赞；返回 false 表示状态已被并发请求改写。 */
    public boolean markLiked(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, LIKED, RedisTtl.LIKE_ONCE));
        } catch (Exception e) {
            log.warn("点赞状态写入不可用, 放行由唯一索引兜底: key={}", key, e);
            return true;
        }
    }

    /** 一条命令完成「撤已赞 + 置冷却」，不留两次写入之间的窗口。 */
    public void markCooling(String key, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, COOLING, ttl);
        } catch (Exception e) {
            log.warn("冷却状态写入失败, 本次不限流: key={}", key, e);
        }
    }

    public boolean pass(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", RedisTtl.LIKE_ONCE));
        } catch (Exception e) {
            log.warn("防重前置拦截不可用, 放行由唯一索引兜底: key={}", key, e);
            return true;
        }
    }

    public void clear(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("防重 gate 清除失败, 等 TTL 自愈: key={}", key, e);
        }
    }
}
