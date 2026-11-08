package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.mapper.VideoMapper;
import com.wisdomhub.video.service.VideoTargetProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VideoTargetProviderServiceImpl implements VideoTargetProviderService {

    private final VideoMapper videoMapper;

    @Override
    public TargetInfoDTO getTarget(String type, Long id) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetType 不能为空");
        }
        int code;
        try {
            code = ContentTargetTypeConstants.codeOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetType 不合法");
        }
        if (code != ContentTargetTypeConstants.VIDEO) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "video-service 不支持该 targetType");
        }

        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getAuthorId, Video::getStatus)
                .eq(Video::getId, id));
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            return missing();
        }
        return found(VideoConstants.PUBLISHED.equals(video.getStatus()), video.getAuthorId());
    }

    private TargetInfoDTO missing() {
        TargetInfoDTO dto = new TargetInfoDTO();
        dto.setExists(false);
        dto.setVisible(false);
        return dto;
    }

    private TargetInfoDTO found(boolean visible, Long ownerId) {
        TargetInfoDTO dto = new TargetInfoDTO();
        dto.setExists(true);
        dto.setVisible(visible);
        dto.setOwnerId(ownerId);
        return dto;
    }
}
