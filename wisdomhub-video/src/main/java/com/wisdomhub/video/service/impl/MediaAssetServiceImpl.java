package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qcloud.cos.exception.CosClientException;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.video.config.UploadProperties;
import com.wisdomhub.video.constants.MediaAssetConstants;
import com.wisdomhub.video.constants.VideoMimeTypes;
import com.wisdomhub.video.domain.dto.UploadTicketDTO;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.domain.vo.MediaAssetStatusVO;
import com.wisdomhub.video.domain.vo.UploadTicketVO;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import com.wisdomhub.video.service.CosObjectService;
import com.wisdomhub.video.service.CosPresignService;
import com.wisdomhub.video.service.MediaAssetService;
import com.wisdomhub.video.service.MpsTranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetServiceImpl implements MediaAssetService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int FAIL_REASON_MAX_LENGTH = 255;

    private final MediaAssetMapper mediaAssetMapper;
    private final CosPresignService cosPresignService;
    private final CosObjectService cosObjectService;
    private final MpsTranscodeService mpsTranscodeService;
    private final UploadProperties uploadProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadTicketVO createUploadTicket(UploadTicketDTO dto) {
        Long ownerId = CurrentUserUtil.id();

        String mimeType = VideoMimeTypes.normalize(dto.getMimeType());
        if (!VideoMimeTypes.isAllowed(mimeType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "不支持的视频格式");
        }
        if (dto.getFileSize() > uploadProperties.getMaxSizeBytes()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "视频大小超过上限");
        }

        String objectKey = buildObjectKey(ownerId, mimeType);
        CosPresignService.PresignedUpload presigned =
                cosPresignService.presignPut(objectKey, mimeType, uploadProperties.getTicketTtlSeconds());

        MediaAsset asset = MediaAsset.builder()
                .ownerId(ownerId)
                .objectKey(objectKey)
                .fileSize(dto.getFileSize())
                .mimeType(mimeType)
                .status(MediaAssetConstants.UPLOADING)
                .build();
        mediaAssetMapper.insert(asset);

        return UploadTicketVO.builder()
                .assetId(asset.getId())
                .objectKey(objectKey)
                .uploadUrl(presigned.getUrl())
                .method("PUT")
                .headers(Map.of("Content-Type", mimeType))
                .expiresAt(presigned.getExpiresAt())
                .build();
    }

    @Override
    public MediaAssetStatusVO completeUpload(Long assetId) {
        MediaAsset asset = requireOwnedAsset(assetId);

        if (isTranscodeStarted(asset.getStatus())) {
            return toStatusVO(asset);
        }
        if (MediaAssetConstants.FAILED.equals(asset.getStatus()) || MediaAssetConstants.REJECTED.equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "资产已失败，不能确认上传");
        }
        if (MediaAssetConstants.UPLOADED.equals(asset.getStatus())) {
            return startTranscode(asset);
        }

        CosObjectService.ObjectStat stat;
        try {
            Optional<CosObjectService.ObjectStat> head = cosObjectService.head(asset.getObjectKey());
            if (head.isEmpty()) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "文件尚未上传");
            }
            stat = head.get();
        } catch (CosClientException e) {
            log.error("HEAD COS 对象失败, assetId={}, objectKey={}", assetId, asset.getObjectKey(), e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "对象存储暂不可用");
        }

        String failReason = validateMetadata(asset, stat);
        if (failReason != null) {
            if (markFailed(asset, failReason)) {
                cosObjectService.deleteQuietly(asset.getObjectKey());
            }
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, failReason);
        }

        MediaAsset update = MediaAsset.builder()
                .status(MediaAssetConstants.UPLOADED)
                .fileSize(stat.getContentLength())
                .build();
        int affected = mediaAssetMapper.update(update, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getOwnerId, asset.getOwnerId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.UPLOADING));
        if (affected == 0) {
            MediaAsset current = mediaAssetMapper.selectById(assetId);
            if (current != null && isTranscodeStarted(current.getStatus())) {
                return toStatusVO(current);
            }
            if (current != null && MediaAssetConstants.UPLOADED.equals(current.getStatus())) {
                return startTranscode(current);
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "资产状态已变化，请刷新后重试");
        }

        asset.setStatus(MediaAssetConstants.UPLOADED);
        asset.setFileSize(stat.getContentLength());
        return startTranscode(asset);
    }

    private MediaAssetStatusVO startTranscode(MediaAsset asset) {
        int claimed = mediaAssetMapper.update(MediaAsset.builder()
                        .status(MediaAssetConstants.TRANSCODING)
                        .build(),
                new LambdaUpdateWrapper<MediaAsset>()
                        .eq(MediaAsset::getId, asset.getId())
                        .eq(MediaAsset::getOwnerId, asset.getOwnerId())
                        .eq(MediaAsset::getStatus, MediaAssetConstants.UPLOADED));
        if (claimed == 0) {
            MediaAsset current = mediaAssetMapper.selectById(asset.getId());
            if (current != null && isTranscodeStarted(current.getStatus())) {
                return toStatusVO(current);
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "资产状态已变化，请刷新后重试");
        }

        asset.setStatus(MediaAssetConstants.TRANSCODING);
        String taskId = null;
        try {
            taskId = mpsTranscodeService.submit(asset);
            mediaAssetMapper.update(MediaAsset.builder().mpsTaskId(taskId).build(),
                    new LambdaUpdateWrapper<MediaAsset>()
                            .eq(MediaAsset::getId, asset.getId())
                            .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING));
            asset.setMpsTaskId(taskId);
            return toStatusVO(asset);
        } catch (Exception e) {
            if (taskId == null) {
                mediaAssetMapper.update(MediaAsset.builder()
                                .status(MediaAssetConstants.UPLOADED)
                                .build(),
                        new LambdaUpdateWrapper<MediaAsset>()
                                .eq(MediaAsset::getId, asset.getId())
                                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING));
            }
            log.error("提交 MPS 转码失败, assetId={}", asset.getId(), e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "转码任务提交失败");
        }
    }

    @Override
    public MediaAssetStatusVO getAsset(Long assetId) {
        return toStatusVO(requireOwnedAsset(assetId));
    }

    private MediaAsset requireOwnedAsset(Long assetId) {
        Long userId = CurrentUserUtil.id();
        MediaAsset asset = mediaAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "资产不存在");
        }
        if (!userId.equals(asset.getOwnerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作他人的资源");
        }
        return asset;
    }

    private boolean isTranscodeStarted(Integer status) {
        return MediaAssetConstants.TRANSCODING.equals(status)
                || MediaAssetConstants.REVIEWING.equals(status)
                || MediaAssetConstants.READY.equals(status);
    }

    private String validateMetadata(MediaAsset asset, CosObjectService.ObjectStat stat) {
        long actualSize = stat.getContentLength();
        if (actualSize <= 0) {
            return "文件为空";
        }
        if (actualSize > uploadProperties.getMaxSizeBytes()) {
            return "文件大小超过上限：" + actualSize;
        }
        if (actualSize != asset.getFileSize()) {
            return "大小与申请不符：声明 " + asset.getFileSize() + "，实际 " + actualSize;
        }

        String actualMime = normalizeContentType(stat.getContentType());
        if (!asset.getMimeType().equals(actualMime)) {
            return "MIME 类型不符：期望 " + asset.getMimeType() + "，实际 " + stat.getContentType();
        }
        return null;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return VideoMimeTypes.normalize(base);
    }

    private boolean markFailed(MediaAsset asset, String reason) {
        String failReason = reason.length() > FAIL_REASON_MAX_LENGTH
                ? reason.substring(0, FAIL_REASON_MAX_LENGTH)
                : reason;
        MediaAsset update = MediaAsset.builder()
                .status(MediaAssetConstants.FAILED)
                .failReason(failReason)
                .build();
        int affected = mediaAssetMapper.update(update, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.UPLOADING));
        if (affected == 0) {
            log.warn("资产状态已变化，跳过失败标记, assetId={}", asset.getId());
            return false;
        }
        return true;
    }

    private MediaAssetStatusVO toStatusVO(MediaAsset asset) {
        return MediaAssetStatusVO.builder()
                .assetId(asset.getId())
                .status(asset.getStatus())
                .fileSize(asset.getFileSize())
                .durationMs(asset.getDurationMs())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .coverKey(asset.getCoverKey())
                .failReason(asset.getFailReason())
                .build();
    }

    private String buildObjectKey(Long ownerId, String mimeType) {
        String month = MONTH_FORMAT.format(LocalDate.now(ZoneOffset.UTC));
        String name = UUID.randomUUID().toString().replace("-", "");
        return "video/" + ownerId + "/" + month + "/" + name + "." + VideoMimeTypes.extensionOf(mimeType);
    }
}
