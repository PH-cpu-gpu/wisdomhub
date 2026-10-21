package com.wisdomhub.common.mq.dlq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "wisdomhub.mq.dlq-replay")
public class DlqReplayProperties {

    private boolean enabled;

    /** 业务队列名，扫描时自动拼 .dlq */
    private List<String> queues = new ArrayList<>();

    private int batchSize = 100;

    private int maxReplays = 3;

    private long confirmTimeoutMillis = 2000;
}
