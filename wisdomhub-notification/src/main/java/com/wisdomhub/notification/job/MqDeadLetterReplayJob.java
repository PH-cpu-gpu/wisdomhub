package com.wisdomhub.notification.job;

import com.wisdomhub.common.mq.dlq.DeadLetterReplayService;
import com.wisdomhub.common.mq.dlq.DlqReplayResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 死信扫描与重投；扫描清单与开关在 notification-service.yaml */
@Component
@RequiredArgsConstructor
public class MqDeadLetterReplayJob {

    private final ObjectProvider<DeadLetterReplayService> replayServiceProvider;

    @XxlJob("mqDeadLetterReplayHandler")
    public void replay() {
        DeadLetterReplayService replayService = replayServiceProvider.getIfAvailable();
        if (replayService == null) {
            XxlJobHelper.handleFail("死信重投未启用: wisdomhub.mq.dlq-replay.enabled=false");
            return;
        }
        try {
            DlqReplayResult result = replayService.replayAll();
            String summary = result.summary();
            XxlJobHelper.log("死信重投完成: " + summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("死信重投失败: " + e.getMessage());
            throw e;
        }
    }
}
