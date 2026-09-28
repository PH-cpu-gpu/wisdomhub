package com.wisdomhub.common.redis.cache;

import lombok.AllArgsConstructor;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.concurrent.Callable;

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
        ValueWrapper valueWrapper = redisCache.get(key);
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
        redisCache.put(key, value);
    }

    @Override
    public void evict(Object key) {
        caffeineCache.invalidate(key);
        redisCache.evict(key);
    }

    @Override
    public void clear() {
        caffeineCache.invalidateAll();
        redisCache.clear();
    }
}
