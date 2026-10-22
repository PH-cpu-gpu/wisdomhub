package com.wisdomhub.common.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** 按 cacheName 覆盖两级 TTL；未配置的沿用默认值 */
@Data
@ConfigurationProperties(prefix = "wisdomhub.cache")
public class MultiLevelCacheProperties {

    private Map<String, Duration> redisTtl = new HashMap<>();

    private Map<String, Duration> caffeineTtl = new HashMap<>();
}
