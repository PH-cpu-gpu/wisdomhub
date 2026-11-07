package com.wisdomhub.interaction.service;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.feign.client.ContentTargetProviderFeign;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 写时校验目标：只读调用内容服务，不存在或不可见按 404 处理。 */
@Service
@RequiredArgsConstructor
public class TargetValidator {

    private final ContentTargetProviderFeign contentTargetProviderFeign;

    public TargetInfoDTO requireVisible(int targetType, Long targetId) {
        String typeName;
        try {
            typeName = ContentTargetTypeConstants.nameOf(targetType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "不支持的目标类型");
        }
        TargetInfoDTO info = contentTargetProviderFeign.getTarget(typeName, targetId);
        if (info == null
                || !Boolean.TRUE.equals(info.getExists())
                || !Boolean.TRUE.equals(info.getVisible())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标不存在或不可见");
        }
        return info;
    }
}
