package com.wisdomhub.common.feign.interceptor;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.security.constants.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextInterceptor implements RequestInterceptor {

    // 向下游透传UserId
    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        // 如果是系统发起的调用不处理
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return;
        }
        String userId = servletRequestAttributes.getRequest().getHeader(SecurityConstants.X_USER_ID);
        if (StrUtil.isNotBlank(userId)) {
            template.header(SecurityConstants.X_USER_ID, userId);
        }
    }
}
