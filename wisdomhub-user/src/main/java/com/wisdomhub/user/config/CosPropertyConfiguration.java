package com.wisdomhub.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosPropertyConfiguration {
    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;
    private String domain;
}