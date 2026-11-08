package com.wisdomhub.video.service;

import com.wisdomhub.common.feign.dto.TargetInfoDTO;

public interface VideoTargetProviderService {

    TargetInfoDTO getTarget(String type, Long id);
}
