package com.wisdomhub.user.service;

public interface FollowCountSyncService {

    /** 以 user_follow 的 COUNT(*) 为准校正 Redis 计数并回写 user 表两列，返回一句供任务日志用的统计 */
    String syncFollowCounts();
}
