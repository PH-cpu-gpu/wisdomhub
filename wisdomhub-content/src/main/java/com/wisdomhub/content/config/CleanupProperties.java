package com.wisdomhub.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wisdomhub.cleanup")
public class CleanupProperties {

    /** 墓碑保留天数；下游重试/对账窗口的最长值 */
    private int retentionDays = 7;

    private int batchSize = 1000;

    private int maxBatchesPerRun = 10;

    private long pauseMillis = 100;

    private int mongoBatchSize = 500;

    /** 删 article/question 墓碑前是否重发删除事件 */
    private boolean publishDeleteEvents = true;
}
