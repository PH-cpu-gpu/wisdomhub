package com.wisdomhub.content.mq;

import com.wisdomhub.common.mq.event.TargetDeletedEvent;
import com.wisdomhub.common.mq.event.payload.TargetDeletedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 删除目标后发 target.deleted；有事务时等提交后再发。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetDeletedEventSender {

    private final EventPublisher eventPublisher;

    public void sendAfterCommit(int targetType, Long targetId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(targetType, targetId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(targetType, targetId);
            }
        });
    }

    private void send(int targetType, Long targetId) {
        try {
            eventPublisher.send(new TargetDeletedEvent(TargetDeletedPayload.builder()
                    .targetType(targetType)
                    .targetId(targetId)
                    .build()));
        } catch (Exception e) {
            log.error("目标删除事件发送失败, targetType={}, targetId={}", targetType, targetId, e);
        }
    }
}
