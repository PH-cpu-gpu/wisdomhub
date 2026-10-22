package com.wisdomhub.common.security.config;

import com.wisdomhub.common.security.filter.InternalSignFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OncePerRequestFilter.class, StringRedisTemplate.class})
@EnableConfigurationProperties(InternalSignProperties.class)
public class InternalSignAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "wisdomhub.security.internal-sign", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<InternalSignFilter> internalSignFilter(InternalSignProperties properties,
                                                                        StringRedisTemplate redisTemplate) {
        validate(properties);
        FilterRegistrationBean<InternalSignFilter> registration =
                new FilterRegistrationBean<>(new InternalSignFilter(properties, redisTemplate));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    private void validate(InternalSignProperties properties) {
        if (!StringUtils.hasText(properties.getSecret()) || properties.getSecret().length() < 16) {
            throw new IllegalStateException("wisdomhub.security.internal-sign.secret 未配置或长度不足 16");
        }
        if (properties.getTimestampWindowSeconds() <= 0 || properties.getNonceTtlSeconds() <= 0) {
            throw new IllegalStateException("签名时间窗与 Nonce TTL 必须为正数");
        }
        if (properties.getMaxBodyBytes() <= 0) {
            throw new IllegalStateException("签名请求体上限必须为正数");
        }
    }
}
