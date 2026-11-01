package com.wisdomhub.common.web.interceptor;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.security.config.AdminProperties;
import com.wisdomhub.common.web.annotation.RequireAdmin;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequireAdminInterceptor implements HandlerInterceptor {

    private final AdminProperties adminProperties;

    public RequireAdminInterceptor(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean required = handlerMethod.getMethodAnnotation(RequireAdmin.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
        if (!required) {
            return true;
        }
        Long userId = CurrentUserUtil.id();
        if (!adminProperties.contains(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "需要管理员权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
    }
}
