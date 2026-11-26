package com.wisdomhub.interaction.service;

import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.interaction.constants.InteractionRedisKeyConstants;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 互动计数快照的 Redis 读缓存（cache-aside）。
 * 计数表仍是唯一事实源，Redis 只卸载读放大；异常一律降级回源，不向上抛。
 * 写成功后等在事务提交再删 key，避免把未提交的新值回填；短 TTL 兜住「读回填 vs 写失效」的竞态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionCountCache {

    private static final int FIELD_COUNT = 3;

    private final StringRedisTemplate stringRedisTemplate;

    public record Counts(int likeCount, int commentCount, int favoriteCount) {

        public static final Counts ZERO = new Counts(0, 0, 0);

        public static Counts of(InteractionCount row) {
            if (row == null) {
                return ZERO;
            }
            return new Counts(nvl(row.getLikeCount()), nvl(row.getCommentCount()), nvl(row.getFavoriteCount()));
        }

        private static int nvl(Integer value) {
            return value == null ? 0 : value;
        }
    }

    /** 命中返回快照；未命中、值损坏或 Redis 异常都返回 null，由调用方回源。 */
    public Counts get(int targetType, Long targetId) {
        String key = InteractionRedisKeyConstants.count(targetType, targetId);
        try {
            return parse(key, stringRedisTemplate.opsForValue().get(key));
        } catch (RuntimeException e) {
            log.warn("互动计数缓存读取失败, 回源 DB: key={}", key, e);
            return null;
        }
    }

    /** 批量命中；只返回可用项，缺失的由调用方回源。 */
    public Map<Long, Counts> getAll(int targetType, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(targetIds);
        List<String> keys = new ArrayList<>(ids.size());
        for (Long targetId : ids) {
            keys.add(InteractionRedisKeyConstants.count(targetType, targetId));
        }
        Map<Long, Counts> result = new HashMap<>(ids.size());
        try {
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return result;
            }
            for (int i = 0; i < ids.size() && i < values.size(); i++) {
                Counts counts = parse(keys.get(i), values.get(i));
                if (counts != null) {
                    result.put(ids.get(i), counts);
                }
            }
        } catch (RuntimeException e) {
            log.warn("互动计数缓存批量读取失败, 回源 DB: targetType={}, size={}", targetType, ids.size(), e);
        }
        return result;
    }

    public void put(int targetType, Long targetId, Counts counts) {
        if (counts == null) {
            return;
        }
        String key = InteractionRedisKeyConstants.count(targetType, targetId);
        try {
            stringRedisTemplate.opsForValue().set(key, format(counts), RedisTtl.INTERACTION_COUNT);
        } catch (RuntimeException e) {
            log.warn("互动计数缓存回填失败, 本次跳过: key={}", key, e);
        }
    }

    public void putAll(int targetType, Map<Long, Counts> counts) {
        if (counts == null || counts.isEmpty()) {
            return;
        }
        long ttlSeconds = RedisTtl.INTERACTION_COUNT.toSeconds();
        try {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                counts.forEach((targetId, value) -> {
                    if (value == null) {
                        return;
                    }
                    connection.stringCommands().setEx(
                            bytes(InteractionRedisKeyConstants.count(targetType, targetId)),
                            ttlSeconds,
                            bytes(format(value)));
                });
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("互动计数缓存批量回填失败: targetType={}, size={}", targetType, counts.size(), e);
        }
    }

    public void evict(int targetType, Long targetId) {
        String key = InteractionRedisKeyConstants.count(targetType, targetId);
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("互动计数缓存失效失败, 等 TTL 自愈: key={}", key, e);
        }
    }

    public void evictAll(int targetType, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(targetIds.size());
        for (Long targetId : targetIds) {
            keys.add(InteractionRedisKeyConstants.count(targetType, targetId));
        }
        try {
            stringRedisTemplate.delete(keys);
        } catch (RuntimeException e) {
            log.warn("互动计数缓存批量失效失败, 等 TTL 自愈: targetType={}, size={}", targetType, keys.size(), e);
        }
    }

    /** 事务内写入成功后调用：提交后再删，避免读路径把未提交的值回填进缓存。 */
    public void evictAfterCommit(int targetType, Long targetId) {
        afterCommit(() -> evict(targetType, targetId));
    }

    public void evictAllAfterCommit(int targetType, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(targetIds);
        afterCommit(() -> evictAll(targetType, ids));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private Counts parse(String key, String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", -1);
        try {
            if (parts.length != FIELD_COUNT) {
                throw new IllegalArgumentException("字段数不是 " + FIELD_COUNT);
            }
            return new Counts(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            log.warn("互动计数缓存值非法, 删除后回源: key={}, value={}", key, value, e);
            deleteQuietly(key);
            return null;
        }
    }

    private void deleteQuietly(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("互动计数缓存坏值删除失败, 等 TTL 自愈: key={}", key, e);
        }
    }

    private String format(Counts counts) {
        return counts.likeCount() + "," + counts.commentCount() + "," + counts.favoriteCount();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}