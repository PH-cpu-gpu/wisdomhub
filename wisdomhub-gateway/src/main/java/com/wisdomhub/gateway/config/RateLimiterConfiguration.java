package com.wisdomhub.gateway.config;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.security.constants.SecurityConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流维度：按用户，游客退回按 IP
 */
@Configuration
public class RateLimiterConfiguration {

    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.X_USER_ID);
            if (StrUtil.isNotBlank(userId)) {
                return Mono.just("u:" + userId);
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            return Mono.just("ip:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress()));
        };
    }
}
