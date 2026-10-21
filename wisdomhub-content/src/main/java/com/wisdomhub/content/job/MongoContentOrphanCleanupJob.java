package com.wisdomhub.content.job;

import com.wisdomhub.content.service.cleanup.CleanupResult;
import com.wisdomhub.content.service.cleanup.MongoContentCleanupService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mongo article_content 孤儿清理。 */
@Component
@RequiredArgsConstructor
public class MongoContentOrphanCleanupJob {

    private final MongoContentCleanupService cleanupService;

    @XxlJob("mongoContentOrphanCleanupHandler")
    public void cleanup() {
        try {
            CleanupResult result = cleanupService.cleanup();
            String summary = result.summary();
            XxlJobHelper.log("Mongo 正文孤儿清理完成: " + summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("Mongo 正文孤儿清理失败: " + e.getMessage());
            throw e;
        }
    }
}
