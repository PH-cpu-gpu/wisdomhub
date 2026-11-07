package com.wisdomhub.interaction.job;

import com.wisdomhub.interaction.service.CountReconcileResult;
import com.wisdomhub.interaction.service.InteractionCountReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 互动计数快照对账，以明细 COUNT(*) 为准。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionCountReconcileJob {

    private final InteractionCountReconcileService interactionCountReconcileService;

    @XxlJob("countReconcileHandler")
    public void reconcile() {
        try {
            CountReconcileResult result = interactionCountReconcileService.reconcile();
            String summary = result.summary();
            XxlJobHelper.log("互动计数对账完成: " + summary);
            log.info("互动计数对账完成: {}", summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("互动计数对账失败: " + e.getMessage());
            throw e;
        }
    }
}
