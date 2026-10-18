package com.wisdomhub.recommendation.service;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;

/** 协同过滤扩展点：返回 [0,1]，默认实现恒 0 */
public interface CollaborativeFilterScorer {

    double score(Long userId, ArticleRecallDTO candidate);
}
