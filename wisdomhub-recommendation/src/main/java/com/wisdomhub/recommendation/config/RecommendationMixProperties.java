package com.wisdomhub.recommendation.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 首页文章/视频混合比例，Nacos 里配 wisdomhub.recommendation.mix.* */
@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.recommendation.mix")
public class RecommendationMixProperties {

    /** 视频占首页条目的比例，0 表示退回纯文章 */
    private double videoShare = 0.3;

    @PostConstruct
    void validate() {
        if (videoShare < 0 || videoShare > 1) {
            throw new IllegalStateException("video-share 必须在 [0,1]");
        }
    }
}
