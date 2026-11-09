package com.wisdomhub.video.mq;

import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.mq.event.BehaviorRecordedEvent;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.video.service.VideoTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 视频浏览行为；发送失败只记日志，不影响播放。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoBehaviorEventPublisher {

    private final EventPublisher eventPublisher;
    private final VideoTagService videoTagService;

    public void viewRecorded(Long videoId, Long userId) {
        if (userId == null) {
            return;
        }
        try {
            eventPublisher.send(new BehaviorRecordedEvent(BehaviorRecordedPayload.builder()
                    .userId(userId)
                    .videoId(videoId)
                    .behaviorType(BehaviorTypeConstants.VIEW)
                    .tagIds(videoTagService.tagIdsOf(videoId))
                    .build()));
        } catch (Exception e) {
            log.warn("视频浏览行为发送失败, videoId={}, userId={}", videoId, userId, e);
        }
    }
}
