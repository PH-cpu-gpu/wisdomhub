package com.wisdomhub.content.config;

import cn.hutool.core.util.StrUtil;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * 没配 admin-addresses 就不建 bean：它是可选基础设施，不该让缺一个配置把整个服务拖垮。
 * 漏配的表现是调度中心触发时报「handler 不存在」，比启动失败好排查。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "xxl.job", name = "admin-addresses")
public class XxlJobConfiguration {

    private final XxlJobPropertyConfiguration xxlJobPropertyConfiguration;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobPropertyConfiguration.Executor executor = xxlJobPropertyConfiguration.getExecutor();
        XxlJobSpringExecutor xxlJobExecutor = new XxlJobSpringExecutor();
        xxlJobExecutor.setEnabled(xxlJobPropertyConfiguration.getEnabled());
        xxlJobExecutor.setAdminAddresses(xxlJobPropertyConfiguration.getAdminAddresses());
        xxlJobExecutor.setAccessToken(xxlJobPropertyConfiguration.getAccessToken());
        xxlJobExecutor.setAppname(executor.getAppname());
        xxlJobExecutor.setPort(executor.getPort());
        xxlJobExecutor.setLogPath(executor.getLogPath());
        xxlJobExecutor.setLogRetentionDays(executor.getLogRetentionDays());
        if (StrUtil.isNotBlank(executor.getIp())) {
            xxlJobExecutor.setIp(executor.getIp());
        }
        if (StrUtil.isNotBlank(executor.getAddress())) {
            xxlJobExecutor.setAddress(executor.getAddress());
        }
        return xxlJobExecutor;
    }
}
