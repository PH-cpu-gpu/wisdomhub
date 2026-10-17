package com.wisdomhub.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * relay 心跳需要 TaskScheduler；单独放一个配置类，避免与 WebSocket 配置类互相依赖。
 */
@Configuration
public class NotificationWebSocketSchedulerConfiguration {

    @Bean
    public ThreadPoolTaskScheduler notificationWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
