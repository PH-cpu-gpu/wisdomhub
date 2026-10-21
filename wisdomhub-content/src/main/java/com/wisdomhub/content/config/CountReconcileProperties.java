package com.wisdomhub.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wisdomhub.reconcile.count")
public class CountReconcileProperties {

    private int batchSize = 500;

    private long pauseMillis = 50;
}
