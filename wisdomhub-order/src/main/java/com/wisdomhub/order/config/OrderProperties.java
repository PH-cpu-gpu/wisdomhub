package com.wisdomhub.order.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 订单业务参数，缺省值可直接跑，调参走 Nacos */
@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.order")
public class OrderProperties {

    /** 待支付订单有效期，分钟 */
    private int payTimeoutMinutes = 15;

    @PostConstruct
    void validate() {
        if (payTimeoutMinutes <= 0) {
            throw new IllegalStateException("订单支付超时时间必须大于 0");
        }
    }
}
