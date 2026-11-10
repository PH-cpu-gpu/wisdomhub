package com.wisdomhub.video.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 审核配置；provider=auto 为默认自动通过。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.video.audit")
public class VideoAuditProperties {

    private String provider = "auto";

    /** 审核中状态超过该分钟数视为超时 */
    private int timeoutMinutes = 10;

    @PostConstruct
    void validate() {
        if (timeoutMinutes <= 0) {
            throw new IllegalStateException("审核超时时间必须大于 0");
        }
    }
}
