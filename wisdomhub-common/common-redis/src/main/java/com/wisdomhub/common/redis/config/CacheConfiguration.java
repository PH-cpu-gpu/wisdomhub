package com.wisdomhub.common.redis.config;

import com.wisdomhub.common.redis.cache.TwoLevelCacheManager;
import com.wisdomhub.common.redis.constants.RedisPrefix;
import com.wisdomhub.common.redis.constants.RedisTtl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration(before = CacheAutoConfiguration.class)
@EnableCaching
@EnableConfigurationProperties(MultiLevelCacheProperties.class)
public class CacheConfiguration {

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                              RedisSerializer<Object> redisValueSerializer,
                                              @Value("${spring.application.name}") String applicationName,
                                              MultiLevelCacheProperties cacheProperties) {
        String prefix = RedisPrefix.APP + serviceOf(applicationName) + ":cache:";
        RedisCacheConfiguration redisCacheConfiguration =
                RedisCacheConfiguration.defaultCacheConfig()
                        .disableCachingNullValues()
                        .entryTtl(RedisTtl.CACHE_DEFAULT)
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer))
                        .computePrefixWith(cacheName -> prefix + cacheName + "::");
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        cacheProperties.getRedisTtl().forEach((cacheName, ttl) ->
                perCache.put(cacheName, redisCacheConfiguration.entryTtl(ttl)));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    // 服务段取服务的短名
    private static String serviceOf(String applicationName) {
        String suffix = "-service";
        return applicationName.endsWith(suffix)
                ? applicationName.substring(0, applicationName.length() - suffix.length())
                : applicationName;
    }

    @Bean
    @Primary
    public TwoLevelCacheManager cacheManager(RedisCacheManager redisCacheManager,
                                             MultiLevelCacheProperties cacheProperties) {
        return new TwoLevelCacheManager(redisCacheManager, cacheProperties);
    }
}
