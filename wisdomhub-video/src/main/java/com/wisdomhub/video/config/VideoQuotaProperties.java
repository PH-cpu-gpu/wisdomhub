package com.wisdomhub.video.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 按用户配额；Nacos 里配 wisdomhub.video.quota.* */
@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.video.quota")
public class VideoQuotaProperties {

    /** 每个用户每日可创建的上传凭证数 */
    private int dailyUploadLimit = 10;

    /** 单个视频时长上限（秒） */
    private long maxDurationSeconds = 1800;

    @PostConstruct
    void validate() {
        if (dailyUploadLimit <= 0) {
            throw new IllegalStateException("每日上传数上限必须大于 0");
        }
        if (maxDurationSeconds <= 0) {
            throw new IllegalStateException("视频时长上限必须大于 0");
        }
    }
}
