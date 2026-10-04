package com.wisdomhub.common.web.util;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;

/**
 * 属主校验。。
 *
 * <p>只在**能操作他人资源**的写接口里用：目标 ID 由客户端传来的那些（如覆盖指定文章）。
 * 创建类接口的属主列直接写当前用户，天然是自己，不必校验。
 *
 * <p>用 403 而不是 404：对不属于自己的资源，不该泄露它存不存在。
 */
public final class OwnerUtil {

    private OwnerUtil() {
    }

    /** 资源属主不是当前登录用户就抛 403 */
    public static void assertOwner(Long ownerId) {
        if (!CurrentUserUtil.id().equals(ownerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作他人的资源");
        }
    }
}
