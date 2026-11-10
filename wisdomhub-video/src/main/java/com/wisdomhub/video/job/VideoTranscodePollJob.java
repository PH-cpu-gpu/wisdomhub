package com.wisdomhub.video.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.video.config.MpsTaskProperties;
import com.wisdomhub.video.config.VideoAuditProperties;
import com.wisdomhub.video.constants.MediaAssetConstants;
import com.wisdomhub.video.domain.AuditResult;
import com.wisdomhub.video.domain.AuditStatus;
import com.wisdomhub.video.domain.MpsTaskResult;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import com.wisdomhub.video.service.MpsTranscodeService;
import com.wisdomhub.video.service.VideoAuditService;
import com.wisdomhub.video.service.VideoQuotaService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodePollJob {

    private static final int FAIL_REASON_MAX_LENGTH = 255;

    private final MediaAssetMapper mediaAssetMapper;
    private final MpsTranscodeService mpsTranscodeService;
    private final MpsTaskProperties properties;
    private final VideoAuditService videoAuditService;
    private final VideoAuditProperties auditProperties;
    private final VideoQuotaService videoQuotaService;

    @XxlJob("videoTranscodePollHandler")
    public void poll() {
        try {
            PollCounts counts = pollRunning();
            int transcodeTimeouts = markTimeouts();
            int auditTimeouts = markAuditTimeouts();
            String summary = "视频转码轮询完成: 就绪 " + counts.ready()
                    + "，审核拒绝 " + counts.rejected()
                    + "，待审核 " + counts.pending()
                    + "，失败 " + counts.failed()
                    + "，转码超时 " + transcodeTimeouts
                    + "，审核超时 " + auditTimeouts;
            XxlJobHelper.log(summary);
            log.info(summary);
            XxlJobHelper.handleSuccess(summary);
        } catch (Exception e) {
            log.error("视频转码轮询失败", e);
            XxlJobHelper.handleFail("视频转码轮询失败: " + e.getMessage());
            throw e;
        }
    }

    private PollCounts pollRunning() {
        List<MediaAsset> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING)
                .isNotNull(MediaAsset::getMpsTaskId)
                .orderByAsc(MediaAsset::getUpdateTime)
                .last("LIMIT " + Math.max(1, properties.getPollBatchSize())));

        int ready = 0;
        int rejected = 0;
        int pending = 0;
        int failed = 0;
        for (MediaAsset asset : assets) {
            try {
                MpsTaskResult result = mpsTranscodeService.describe(asset.getMpsTaskId());
                if (!result.isFinished()) {
                    continue;
                }
                if (result.isSuccess()) {
                    // 转码前探测失败时由这里兜底
                    String violation = videoQuotaService.durationViolation(result.getDurationMs());
                    if (violation != null) {
                        if (markFailed(asset, violation)) {
                            failed++;
                        }
                        continue;
                    }
                    if (!markReviewing(asset, result)) {
                        continue;
                    }
                    switch (auditAndFinish(asset)) {
                        case READY -> ready++;
                        case REJECTED -> rejected++;
                        case PENDING -> pending++;
                        case FAILED -> failed++;
                        case SKIPPED -> {
                        }
                    }
                } else if (markFailed(asset, result.getFailReason())) {
                    failed++;
                }
            } catch (Exception e) {
                log.error("轮询 MPS 任务异常, assetId={}, taskId={}",
                        asset.getId(), asset.getMpsTaskId(), e);
            }
        }
        return new PollCounts(ready, rejected, pending, failed);
    }

    /** 转码成功先落元数据并置审核中，再从审核中推进到最终状态。 */
    private boolean markReviewing(MediaAsset asset, MpsTaskResult result) {
        MediaAsset update = MediaAsset.builder()
                .status(MediaAssetConstants.REVIEWING)
                .durationMs(result.getDurationMs())
                .width(result.getWidth())
                .height(result.getHeight())
                .coverKey(result.getCoverKey())
                .build();
        int affected = mediaAssetMapper.update(update, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING));
        if (affected > 0) {
            log.info("视频转码完成，进入审核, assetId={}, playlist={}, cover={}",
                    asset.getId(), result.getPlaylistKey(), result.getCoverKey());
        }
        return affected > 0;
    }

    private AuditOutcome auditAndFinish(MediaAsset asset) {
        AuditResult audit;
        try {
            audit = videoAuditService.audit(asset);
        } catch (Exception e) {
            log.error("视频审核异常, assetId={}", asset.getId(), e);
            return markAuditStatus(asset, MediaAssetConstants.FAILED,
                    "审核失败: " + e.getMessage()) ? AuditOutcome.FAILED : AuditOutcome.SKIPPED;
        }
        if (audit == null || audit.status() == null) {
            log.error("视频审核服务返回空结果, assetId={}", asset.getId());
            return markAuditStatus(asset, MediaAssetConstants.FAILED,
                    "审核服务未返回结果") ? AuditOutcome.FAILED : AuditOutcome.SKIPPED;
        }
        if (audit.status() == AuditStatus.PENDING) {
            return AuditOutcome.PENDING;
        }
        if (audit.status() == AuditStatus.PASSED) {
            return markAuditStatus(asset, MediaAssetConstants.READY, null)
                    ? AuditOutcome.READY : AuditOutcome.SKIPPED;
        }
        String reason = audit.reason() == null || audit.reason().isBlank()
                ? "审核未通过" : audit.reason();
        return markAuditStatus(asset, MediaAssetConstants.REJECTED, reason)
                ? AuditOutcome.REJECTED : AuditOutcome.SKIPPED;
    }

    private boolean markAuditStatus(MediaAsset asset, Integer status, String reason) {
        String failReason = reason == null ? null : truncate(reason, "审核失败");
        int affected = mediaAssetMapper.update(null, new LambdaUpdateWrapper<MediaAsset>()
                .set(MediaAsset::getStatus, status)
                .set(MediaAsset::getFailReason, failReason)
                .set(MediaAsset::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.REVIEWING));
        if (affected == 0) {
            log.warn("资产状态已变化，跳过审核结果写入, assetId={}, targetStatus={}", asset.getId(), status);
            return false;
        }
        if (MediaAssetConstants.REJECTED.equals(status)) {
            log.warn("视频审核拒绝, assetId={}, reason={}", asset.getId(), failReason);
        } else if (MediaAssetConstants.FAILED.equals(status)) {
            log.warn("视频审核失败, assetId={}, reason={}", asset.getId(), failReason);
        } else {
            log.info("视频审核通过, assetId={}", asset.getId());
        }
        return true;
    }

    private boolean markFailed(MediaAsset asset, String reason) {
        String failReason = truncate(reason, "转码失败");
        MediaAsset update = MediaAsset.builder()
                .status(MediaAssetConstants.FAILED)
                .failReason(failReason)
                .build();
        int affected = mediaAssetMapper.update(update, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING));
        if (affected > 0) {
            log.warn("视频转码失败, assetId={}, reason={}", asset.getId(), failReason);
        }
        return affected > 0;
    }

    private int markTimeouts() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minusMinutes(Math.max(1, properties.getTimeoutMinutes()));
        List<MediaAsset> stale = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING)
                .lt(MediaAsset::getUpdateTime, cutoff)
                .last("LIMIT 100"));
        int failed = 0;
        for (MediaAsset asset : stale) {
            if (markFailed(asset, "转码超时")) {
                failed++;
            }
        }
        return failed;
    }

    private int markAuditTimeouts() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minusMinutes(auditProperties.getTimeoutMinutes());
        List<MediaAsset> stale = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getStatus, MediaAssetConstants.REVIEWING)
                .lt(MediaAsset::getUpdateTime, cutoff)
                .last("LIMIT 100"));
        int failed = 0;
        for (MediaAsset asset : stale) {
            if (markAuditStatus(asset, MediaAssetConstants.FAILED, "审核超时")) {
                failed++;
            }
        }
        return failed;
    }

    private String truncate(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > FAIL_REASON_MAX_LENGTH
                ? value.substring(0, FAIL_REASON_MAX_LENGTH)
                : value;
    }

    private record PollCounts(int ready, int rejected, int pending, int failed) {
    }

    private enum AuditOutcome {
        READY, REJECTED, PENDING, FAILED, SKIPPED
    }
}
