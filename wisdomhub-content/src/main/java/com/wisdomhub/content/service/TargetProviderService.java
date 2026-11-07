package com.wisdomhub.content.service;

import com.wisdomhub.common.feign.dto.TargetInfoDTO;

/** 互动写时校验：目标是否存在、是否公开可见、作者是谁。只读，不反向依赖 interaction。 */
public interface TargetProviderService {

    TargetInfoDTO getTarget(String type, Long id);
}
