package com.wisdomhub.content.job;

import com.wisdomhub.content.service.TagProposalService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 待审提案超过保留期自动置为过期。 */
@Component
@RequiredArgsConstructor
public class TagProposalExpireJob {

    private final TagProposalService tagProposalService;

    @XxlJob("tagProposalExpireHandler")
    public void expire() {
        try {
            int expired = tagProposalService.expirePending();
            XxlJobHelper.log("标签提案过期处理完成: expired=" + expired);
            XxlJobHelper.handleSuccess("expired=" + expired);
        } catch (Exception e) {
            XxlJobHelper.handleFail("标签提案过期处理失败: " + e.getMessage());
            throw e;
        }
    }
}
