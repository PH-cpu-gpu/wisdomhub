package com.wisdomhub.content.job;

import com.wisdomhub.content.service.ArticleViewCountSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阅读量的 Redis → article.view_count 快照回写。
 */
@Component
@RequiredArgsConstructor
public class ArticleViewCountSyncJob {

    private final ArticleViewCountSyncService articleViewCountSyncService;

    @XxlJob("articleViewCountSyncHandler")
    public void syncViewCounts() {
        String result = articleViewCountSyncService.syncViewCounts();
        XxlJobHelper.log(result);
        XxlJobHelper.handleSuccess(result);
    }
}
