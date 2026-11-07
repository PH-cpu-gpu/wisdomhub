package com.wisdomhub.interaction.job;

import com.wisdomhub.interaction.service.CommentCleanupResult;
import com.wisdomhub.interaction.service.InteractionCommentCleanupService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 互动评论墓碑物理清理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionCommentCleanupJob {

    private final InteractionCommentCleanupService interactionCommentCleanupService;

    @XxlJob("interactionCommentCleanupHandler")
    public void cleanup() {
        try {
            CommentCleanupResult result = interactionCommentCleanupService.cleanup();
            String summary = result.summary();
            XxlJobHelper.log("互动评论墓碑清理完成: " + summary);
            log.info("互动评论墓碑清理完成: {}", summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("互动评论墓碑清理失败: " + e.getMessage());
            throw e;
        }
    }
}
