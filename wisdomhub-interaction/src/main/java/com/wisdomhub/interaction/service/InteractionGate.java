package com.wisdomhub.interaction.service;

import com.wisdomhub.common.redis.constants.RedisTtl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 点赞/收藏的 Redis 防重快路径；Redis 不可用时放行，正确性由唯一键兜底。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionGate {

    private final StringRedisTemplate stringRedisTemplate;

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
