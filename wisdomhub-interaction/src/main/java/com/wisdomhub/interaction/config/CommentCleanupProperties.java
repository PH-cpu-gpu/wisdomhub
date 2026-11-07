package com.wisdomhub.interaction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wisdomhub.cleanup.comment")
public class CommentCleanupProperties {

    private int retentionDays = 7;

    private int batchSize = 1000;

    private int maxBatchesPerRun = 10;

    private long pauseMillis = 100;
}
