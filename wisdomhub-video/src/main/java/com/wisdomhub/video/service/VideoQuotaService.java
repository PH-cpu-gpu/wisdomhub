package com.wisdomhub.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.video.config.VideoQuotaProperties;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 按用户配额：每日上传数与视频时长。 */
@Service
@RequiredArgsConstructor
public class VideoQuotaService {

    private final MediaAssetMapper mediaAssetMapper;
    private final VideoQuotaProperties properties;

    /** 按 UTC 当日已创建的上传凭证数校验每日额度。 */
    public void checkDailyUpload(Long ownerId) {
        LocalDateTime dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        Long used = mediaAssetMapper.selectCount(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getOwnerId, ownerId)
                .ge(MediaAsset::getCreateTime, dayStart)
                .lt(MediaAsset::getCreateTime, dayStart.plusDays(1)));
        if (used != null && used >= properties.getDailyUploadLimit()) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "今日上传次数已达上限（" + properties.getDailyUploadLimit() + " 次）");
        }
    }

    /** 返回超限文案；未超限或时长未知返回 null。 */
    public String durationViolation(Long durationMs) {
        if (durationMs == null) {
            return null;
        }
        long maxMs = properties.getMaxDurationSeconds() * 1000L;
        if (durationMs <= maxMs) {
            return null;
        }
        return "视频时长超过上限（最长 " + formatMaxDuration() + "）";
    }

    private String formatMaxDuration() {
        long seconds = properties.getMaxDurationSeconds();
        return seconds % 60 == 0 ? (seconds / 60) + " 分钟" : seconds + " 秒";
    }
}
