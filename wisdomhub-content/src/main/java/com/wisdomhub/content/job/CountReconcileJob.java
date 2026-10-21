package com.wisdomhub.content.job;

import com.wisdomhub.content.service.reconcile.CountReconcileResult;
import com.wisdomhub.content.service.reconcile.CountReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 计数快照列对账，以明细 COUNT(*) 为准。 */
@Component
@RequiredArgsConstructor
public class CountReconcileJob {

    private final CountReconcileService countReconcileService;

    @XxlJob("countReconcileHandler")
    public void reconcile() {
        try {
            CountReconcileResult result = countReconcileService.reconcile();
            String summary = result.summary();
            XxlJobHelper.log("计数快照对账完成: " + summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("计数快照对账失败: " + e.getMessage());
            throw e;
        }
    }
}
