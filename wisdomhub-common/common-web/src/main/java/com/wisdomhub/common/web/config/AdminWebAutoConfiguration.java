package com.wisdomhub.common.web.config;

import com.wisdomhub.common.security.config.AdminProperties;
import com.wisdomhub.common.web.interceptor.RequireAdminInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminWebAutoConfiguration implements WebMvcConfigurer {

    private final AdminProperties adminProperties;

    public AdminWebAutoConfiguration(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireAdminInterceptor(adminProperties)).order(1);
    }
}
