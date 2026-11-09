package com.wisdomhub.video.mq;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.TargetDeletedEvent;
import com.wisdomhub.common.mq.event.VideoCreatedEvent;
import com.wisdomhub.common.mq.event.VideoDeletedEvent;
import com.wisdomhub.common.mq.event.VideoUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.TargetDeletedPayload;
import com.wisdomhub.common.mq.event.payload.VideoDeletedPayload;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 视频事件统一在事务提交后发送，避免消费者回查不到未提交的数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventSender {

    private final EventPublisher eventPublisher;

    public void createdAfterCommit(VideoEventPayload payload) {
        sendAfterCommit(new VideoCreatedEvent(payload));
    }

    public void updatedAfterCommit(VideoEventPayload payload) {
        sendAfterCommit(new VideoUpdatedEvent(payload));
    }

    public void deletedAfterCommit(Long videoId, Long authorId) {
        sendAfterCommit(new VideoDeletedEvent(VideoDeletedPayload.builder()
                .videoId(videoId)
                .authorId(authorId)
                .build()));
        sendAfterCommit(new TargetDeletedEvent(TargetDeletedPayload.builder()
                .targetType(ContentTargetTypeConstants.VIDEO)
                .targetId(videoId)
                .build()));
    }

    private void sendAfterCommit(BaseEvent<?> event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    private void send(BaseEvent<?> event) {
        try {
            eventPublisher.send(event);
        } catch (Exception e) {
            log.error("视频事件发送失败, eventType={}, eventId={}", event.getEventType(), event.getEventId(), e);
        }
    }
}
