package com.wisdomhub.search.job;

import com.wisdomhub.search.service.reconcile.ReconcileResult;
import com.wisdomhub.search.service.reconcile.UserReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** user_index 对账。任务参数 full 走全量（含孤儿清理），空走近 N 小时增量。 */
@Component
@RequiredArgsConstructor
public class UserReconcileJob {

    private final UserReconcileService userReconcileService;

    @XxlJob("userReconcileHandler")
    public void reconcile() {
        boolean full = "full".equalsIgnoreCase(XxlJobHelper.getJobParam());
        try {
            ReconcileResult result = userReconcileService.reconcile(full);
            String summary = (full ? "full" : "incremental") + ", " + result.summary();
            XxlJobHelper.log("user_index 对账完成: " + summary);
            XxlJobHelper.handleSuccess(summary);
        } catch (Exception e) {
            XxlJobHelper.handleFail("user_index 对账失败: " + e.getMessage());
            throw e;
        }
    }
}
