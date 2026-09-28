package com.wisdomhub.common.redis.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TwoLevelCacheManager implements CacheManager {

    private final Map<String, Cache> caches;
    private final RedisCacheManager redisCacheManager;

    public TwoLevelCacheManager(RedisCacheManager redisCacheManager) {
        this.caches = new ConcurrentHashMap<>();
        this.redisCacheManager = redisCacheManager;
    }

    @Override
    public @Nullable Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::createCache);
    }

    private Cache createCache(String name) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        org.springframework.cache.Cache redisCache = redisCacheManager.getCache(name);
        return new TwoLevelCache(name, caffeineCache, redisCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        Set<String> names = new HashSet<>(caches.keySet());
        names.addAll(redisCacheManager.getCacheNames());
        return names;
    }
}
