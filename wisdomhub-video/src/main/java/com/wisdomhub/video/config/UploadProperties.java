package com.wisdomhub.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.video.upload")
public class UploadProperties {
    /** 单文件上限（字节） */
    private long maxSizeBytes = 2L * 1024 * 1024 * 1024;

    /** 预签名 URL 有效期（秒） */
    private int ticketTtlSeconds = 900;

    /** 待上传资产超时（分钟）：须覆盖凭证有效期与大文件慢速直传，超时置失败避免孤儿资产 */
    private int uploadTimeoutMinutes = 120;
}
