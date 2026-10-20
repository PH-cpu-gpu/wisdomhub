package com.wisdomhub.common.job.config;

import cn.hutool.core.util.StrUtil;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 没配 admin-addresses 就不建 bean。
 */
@AutoConfiguration
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(prefix = "xxl.job", name = "admin-addresses")
public class XxlJobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executor = properties.getExecutor();
        XxlJobSpringExecutor xxlJobExecutor = new XxlJobSpringExecutor();
        xxlJobExecutor.setEnabled(properties.getEnabled());
        xxlJobExecutor.setAdminAddresses(properties.getAdminAddresses());
        xxlJobExecutor.setAccessToken(properties.getAccessToken());
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
