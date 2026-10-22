package com.wisdomhub.common.security.config;

import com.wisdomhub.common.security.constants.InternalSignConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务签名配置；secret 必须由 Nacos 下发，不落仓库。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wisdomhub.security.internal-sign")
public class InternalSignProperties {

    private boolean enabled = false;

    private String secret;

    /** 调用方标识，缺省取 spring.application.name */
    private String serviceName;

    /** 为空表示不限制调用方服务名 */
    private List<String> allowedServices = new ArrayList<>();

    private long timestampWindowSeconds = InternalSignConstants.DEFAULT_TIMESTAMP_WINDOW_SECONDS;

    private long nonceTtlSeconds = InternalSignConstants.DEFAULT_NONCE_TTL_SECONDS;

    /** 验签前缓存请求体的上限，防止未认证的大包占内存 */
    private int maxBodyBytes = 1024 * 1024;

    // 不生成 toString，避免 secret 进日志
}
