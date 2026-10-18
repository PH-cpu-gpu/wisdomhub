package com.wisdomhub.recommendation.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 打分权重与热度系数，缺省值可直接跑，调参走 Nacos */
@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.recommendation.scoring")
public class ScoringProperties {

    private double interestWeight = 0.5;

    private double hotWeight = 0.3;

    private double freshnessWeight = 0.2;

    private double cfWeight = 0.0;

    private int freshnessHalfLifeDays = 30;

    private double viewWeight = 1;

    private double likeWeight = 3;

    private double commentWeight = 2;

    private double favoriteWeight = 4;

    @PostConstruct
    void validate() {
        if (interestWeight < 0 || hotWeight < 0 || freshnessWeight < 0 || cfWeight < 0) {
            throw new IllegalStateException("打分权重不能为负");
        }
        if (interestWeight + hotWeight + freshnessWeight + cfWeight <= 0) {
            throw new IllegalStateException("打分权重之和必须大于 0");
        }
        if (freshnessHalfLifeDays <= 0) {
            throw new IllegalStateException("时效半衰期必须大于 0");
        }
        if (viewWeight < 0 || likeWeight < 0 || commentWeight < 0 || favoriteWeight < 0) {
            throw new IllegalStateException("热度系数不能为负");
        }
    }
}
