package com.wisdomhub.video.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.video.config.MpsTaskProperties;
import com.wisdomhub.video.constants.MediaAssetConstants;
import com.wisdomhub.video.domain.MpsTaskResult;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import com.wisdomhub.video.service.MpsTranscodeService;
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

    @XxlJob("videoTranscodePollHandler")
    public void poll() {
        try {
            PollCounts counts = pollRunning();
            int timeoutFailed = markTimeouts();
            String summary = "视频转码轮询完成: 成功 " + counts.ready()
                    + "，MPS 失败 " + counts.failed()
                    + "，超时失败 " + timeoutFailed;
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
        int failed = 0;
        for (MediaAsset asset : assets) {
            try {
                MpsTaskResult result = mpsTranscodeService.describe(asset.getMpsTaskId());
                if (!result.isFinished()) {
                    continue;
                }
                if (result.isSuccess()) {
                    if (markReady(asset, result)) {
                        ready++;
                    }
                } else if (markFailed(asset, result.getFailReason())) {
                    failed++;
                }
            } catch (Exception e) {
                log.error("轮询 MPS 任务异常, assetId={}, taskId={}",
                        asset.getId(), asset.getMpsTaskId(), e);
            }
        }
        return new PollCounts(ready, failed);
    }

    private boolean markReady(MediaAsset asset, MpsTaskResult result) {
        MediaAsset update = MediaAsset.builder()
                .status(MediaAssetConstants.READY)
                .durationMs(result.getDurationMs())
                .width(result.getWidth())
                .height(result.getHeight())
                .coverKey(result.getCoverKey())
                .build();
        int affected = mediaAssetMapper.update(update, new LambdaUpdateWrapper<MediaAsset>()
                .eq(MediaAsset::getId, asset.getId())
                .eq(MediaAsset::getStatus, MediaAssetConstants.TRANSCODING));
        if (affected > 0) {
            log.info("视频转码完成, assetId={}, playlist={}, cover={}",
                    asset.getId(), result.getPlaylistKey(), result.getCoverKey());
        }
        return affected > 0;
    }

    private boolean markFailed(MediaAsset asset, String reason) {
        String failReason = truncate(reason);
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

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "转码失败";
        }
        return value.length() > FAIL_REASON_MAX_LENGTH
                ? value.substring(0, FAIL_REASON_MAX_LENGTH)
                : value;
    }

    private record PollCounts(int ready, int failed) {
    }
}
