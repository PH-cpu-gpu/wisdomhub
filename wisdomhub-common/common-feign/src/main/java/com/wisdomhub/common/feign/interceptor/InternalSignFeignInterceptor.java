package com.wisdomhub.common.feign.interceptor;

import com.wisdomhub.common.security.constants.InternalSignConstants;
import com.wisdomhub.common.security.config.InternalSignProperties;
import com.wisdomhub.common.security.util.InternalSignUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * 调用 /internal/** 时自动加签名；每次请求（含重试）都换新 Nonce。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class InternalSignFeignInterceptor implements RequestInterceptor {

    private final InternalSignProperties properties;
    private final String serviceName;

    public InternalSignFeignInterceptor(InternalSignProperties properties, String serviceName) {
        this.properties = properties;
        this.serviceName = serviceName;
    }

    @Override
    public void apply(RequestTemplate template) {
        String pathWithQuery = resolvePathWithQuery(template);
        if (!pathWithQuery.equals(InternalSignConstants.PATH_PREFIX)
                && !pathWithQuery.startsWith(InternalSignConstants.PATH_PREFIX + "/")) {
            return;
        }
        if (!StringUtils.hasText(properties.getSecret())) {
            throw new IllegalStateException("wisdomhub.security.internal-sign.secret 未配置");
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = InternalSignUtil.canonicalString(template.method(), pathWithQuery,
                timestamp, nonce, template.body());
        String signature = InternalSignUtil.sign(canonical, properties.getSecret());

        setHeader(template, InternalSignConstants.HEADER_SERVICE_NAME, serviceName);
        setHeader(template, InternalSignConstants.HEADER_TIMESTAMP, timestamp);
        setHeader(template, InternalSignConstants.HEADER_NONCE, nonce);
        setHeader(template, InternalSignConstants.HEADER_SIGNATURE, signature);
    }

    /** @FeignClient(path) 在 Target 里，不在 template.url() 里，必须自己拼回去。 */
    private String resolvePathWithQuery(RequestTemplate template) {
        String url = template.url();
        if (isAbsolute(url)) {
            return rawPathAndQuery(URI.create(url));
        }
        Target<?> target = template.feignTarget();
        if (target != null && isAbsolute(target.url())) {
            return joinPath(rawPathAndQuery(URI.create(target.url())), url);
        }
        return url;
    }

    private boolean isAbsolute(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String rawPathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private String joinPath(String basePath, String pathAndQuery) {
        if (basePath.endsWith("/") && pathAndQuery.startsWith("/")) {
            return basePath.substring(0, basePath.length() - 1) + pathAndQuery;
        }
        return basePath + pathAndQuery;
    }

    private void setHeader(RequestTemplate template, String name, String value) {
        template.removeHeader(name);
        template.header(name, value);
    }
}
