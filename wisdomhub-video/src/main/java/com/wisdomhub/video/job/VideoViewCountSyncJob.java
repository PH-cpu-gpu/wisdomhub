package com.wisdomhub.video.job;

import com.wisdomhub.video.service.VideoViewCountSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoViewCountSyncJob {

    private final VideoViewCountSyncService videoViewCountSyncService;

    @XxlJob("videoViewCountSyncHandler")
    public void syncViewCounts() {
        try {
            String summary = videoViewCountSyncService.syncViewCounts();
            XxlJobHelper.log(summary);
            log.info(summary);
            XxlJobHelper.handleSuccess(summary);
        } catch (Exception e) {
            XxlJobHelper.handleFail("视频播放量落库失败: " + e.getMessage());
            throw e;
        }
    }
}
