package com.wisdomhub.content.job;

import com.wisdomhub.content.service.promote.TagCandidatePromoteResult;
import com.wisdomhub.content.service.promote.TagCandidatePromoteService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 未收录实体按文章证据升级成待审提案；不配调度就只在手工触发时执行。 */
@Component
@RequiredArgsConstructor
public class TagCandidatePromoteJob {

    private final TagCandidatePromoteService tagCandidatePromoteService;

    @XxlJob("tagCandidatePromoteHandler")
    public void promote() {
        try {
            TagCandidatePromoteResult result = tagCandidatePromoteService.promote();
            String summary = result.summary();
            XxlJobHelper.log("未收录候选升级完成: " + summary);
            if (result.hasFailure()) {
                XxlJobHelper.handleFail(summary);
            } else {
                XxlJobHelper.handleSuccess(summary);
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail("未收录候选升级失败: " + e.getMessage());
            throw e;
        }
    }
}
