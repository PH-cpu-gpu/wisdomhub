package com.wisdomhub.content.service;

public interface ArticleViewCountSyncService {

    /** 把 Redis 的阅读量计数刷回 article.view_count 快照，返回一句供任务日志用的统计 */
    String syncViewCounts();
}
