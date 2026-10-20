package com.wisdomhub.search.job;

import com.wisdomhub.search.service.reconcile.ReconcileResult;
import com.wisdomhub.search.service.reconcile.TagReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** tag_index 对账，标签量小，每次全量。 */
@Component
@RequiredArgsConstructor
public class TagReconcileJob {

    private final TagReconcileService tagReconcileService;

    @XxlJob("tagReconcileHandler")
    public void reconcile() {
        try {
            ReconcileResult result = tagReconcileService.reconcile();
            XxlJobHelper.log("tag_index 对账完成: " + result.summary());
            XxlJobHelper.handleSuccess(result.summary());
        } catch (Exception e) {
            XxlJobHelper.handleFail("tag_index 对账失败: " + e.getMessage());
            throw e;
        }
    }
}
