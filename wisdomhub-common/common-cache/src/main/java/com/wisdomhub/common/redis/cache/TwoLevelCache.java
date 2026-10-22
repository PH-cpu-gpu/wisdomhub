package com.wisdomhub.common.redis.cache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 两级缓存：L1 Caffeine（本地）+ L2 Redis（共享）。
 * <p>
 * L2 故障一律降级、不向上抛：缓存是可选优化，不该把接口拖挂。读退化为 miss（回源），
 * 写只写 L1，失效尽力而为 —— 残余脏值由 TTL 兜底（L1 5 分钟 / L2 30 分钟）。
 */
@Slf4j
@AllArgsConstructor
public class TwoLevelCache implements org.springframework.cache.Cache {

    private String name;
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache;
    private org.springframework.cache.Cache redisCache;

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Nullable
    @Override
    public ValueWrapper get(Object key) {
        Object caffeineValue = caffeineCache.getIfPresent(key);
        if (caffeineValue != null) {
            return new SimpleValueWrapper(caffeineValue);
        }
        ValueWrapper valueWrapper;
        try {
            valueWrapper = redisCache.get(key);
        } catch (RuntimeException e) {
            // Redis 不可用：降级成 miss，交由调用方回源。
            log.warn("二级缓存读取失败, 降级为 miss: cache={}, key={}", name, key, e);
            return null;
        }
        if (valueWrapper != null) {
            caffeineCache.put(key, Objects.requireNonNull(valueWrapper.get()));
            return valueWrapper;
        }
        return null;
    }

    @Nullable
    @Override
    public <T> T get(Object key, @Nullable Class<T> type) {
        ValueWrapper valueWrapper = get(key);
        if (valueWrapper == null) {
            return null;
        }
        Object value = valueWrapper.get();
        if (type != null && value != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T)value;
    }

    @Nullable
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper valueWrapper = get(key);
        if (valueWrapper != null) {
            T result = (T) valueWrapper.get();
            return result;
        }
        try {
            T value = valueLoader.call();
            this.put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (value == null) {
            evict(key);
            return;
        }
        caffeineCache.put(key, value);
        try {
            redisCache.put(key, value);
        } catch (RuntimeException e) {
            log.warn("二级缓存写入失败, 仅写入本地缓存: cache={}, key={}", name, key, e);
        }
    }

    @Override
    public void evict(Object key) {
        caffeineCache.invalidate(key);
        try {
            redisCache.evict(key);
        } catch (RuntimeException e) {
            log.warn("二级缓存失效失败, 残余旧值待 TTL 过期: cache={}, key={}", name, key, e);
        }
    }

    @Override
    public void clear() {
        caffeineCache.invalidateAll();
        try {
            redisCache.clear();
        } catch (RuntimeException e) {
            log.warn("二级缓存清空失败, 残余旧值待 TTL 过期: cache={}", name, e);
        }
    }
}
