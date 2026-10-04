package com.wisdomhub.user.job;

import com.wisdomhub.user.service.FollowCountSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 关注数 / 粉丝数的 Redis → MySQL 回写与对账。
 */
@Component
@RequiredArgsConstructor
public class UserCountSyncJob {

    private final FollowCountSyncService followCountSyncService;

    @XxlJob("userCountSyncHandler")
    public void syncFollowCounts() {
        String result = followCountSyncService.syncFollowCounts();
        XxlJobHelper.log(result);
        XxlJobHelper.handleSuccess(result);
    }
}
