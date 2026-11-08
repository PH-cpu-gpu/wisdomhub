package com.wisdomhub.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.video.mps")
public class MpsTaskProperties {
    private Long transcodeTemplateId;
    private Long snapshotTemplateId;
    private int pollBatchSize = 50;
    private int timeoutMinutes = 120;
    private float snapshotTimeOffsetSeconds = 1f;
}
