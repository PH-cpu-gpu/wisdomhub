package com.wisdomhub.search.job;

import com.wisdomhub.search.service.reconcile.QuestionReconcileService;
import com.wisdomhub.search.service.reconcile.ReconcileResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** question_index 对账。任务参数 full 走全量（含孤儿扫描），空走近 N 小时增量。 */
@Component
@RequiredArgsConstructor
public class QuestionReconcileJob {

    private final QuestionReconcileService questionReconcileService;

    @XxlJob("questionReconcileHandler")
    public void reconcile() {
        boolean full = "full".equalsIgnoreCase(XxlJobHelper.getJobParam());
        try {
            ReconcileResult result = questionReconcileService.reconcile(full);
            String summary = (full ? "full" : "incremental") + ", " + result.summary();
            XxlJobHelper.log("question_index 对账完成: " + summary);
            XxlJobHelper.handleSuccess(summary);
        } catch (Exception e) {
            XxlJobHelper.handleFail("question_index 对账失败: " + e.getMessage());
            throw e;
        }
    }
}
