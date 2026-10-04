package com.wisdomhub.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobPropertyConfiguration {

    /**
     * 调度中心地址，多个用逗号分隔
     */
    private String adminAddresses;

    /**
     * 与调度中心一致的通信 token，留空表示不校验
     */
    private String accessToken;

    /**
     * 是否启用执行器。
     */
    private Boolean enabled = true;

    private Executor executor = new Executor();

    @Data
    public static class Executor {

        /**
         * 执行器名，要与调度中心「执行器管理」里配置的 AppName 一致
         */
        private String appname;

        /**
         * 执行器端口，调度中心靠回连这个端口触发任务
         */
        private int port = 9999;

        /**
         * 留空则自动探测本机 IP。调度中心跑在容器里连不上宿主机时，改成 host.docker.internal
         */
        private String ip;

        /**
         * 显式指定注册地址（http://host:port），优先级高于 ip + port
         */
        private String address;

        private String logPath = "./logs/xxl-job";

        private int logRetentionDays = 30;
    }
}
