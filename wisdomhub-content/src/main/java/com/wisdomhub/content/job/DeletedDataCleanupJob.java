package com.wisdomhub.content.job;

import com.wisdomhub.content.service.cleanup.CleanupResult;
import com.wisdomhub.content.service.cleanup.DeletedDataCleanupService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 墓碑物理清理；参数空/all，或 comment|answer|question|article|column 单跑。 */
@Component
@RequiredArgsConstructor
public class DeletedDataCleanupJob {

    private final DeletedDataCleanupService cleanupService;

    @XxlJob("deletedDataCleanupHandler")
    public void cleanup() {
        try {
            CleanupResult result = cleanupService.cleanup(XxlJobHelper.getJobParam());
            String summary = result.summary();
            XxlJobHelper.log("墓碑清理完成: " + summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("墓碑清理失败: " + e.getMessage());
            throw e;
        }
    }
}
