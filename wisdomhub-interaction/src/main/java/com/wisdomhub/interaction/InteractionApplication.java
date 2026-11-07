package com.wisdomhub.interaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = {"com.wisdomhub.interaction", "com.wisdomhub.common.feign.client"})
public class InteractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteractionApplication.class, args);
    }
}
