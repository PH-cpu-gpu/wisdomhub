package com.wisdomhub.common.web.util;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.security.context.UserContext;

public final class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    /** 取当前登录用户 ID。网关保证非白名单路径一定带 X-User-Id，这里只是防御 */
    public static Long id() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
