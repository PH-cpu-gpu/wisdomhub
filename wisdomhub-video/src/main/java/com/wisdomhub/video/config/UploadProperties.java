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
}
