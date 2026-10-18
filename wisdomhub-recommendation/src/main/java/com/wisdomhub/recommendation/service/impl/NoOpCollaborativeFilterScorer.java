package com.wisdomhub.recommendation.service.impl;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.service.CollaborativeFilterScorer;

/** 占位实现；将来注册新的 CollaborativeFilterScorer Bean 即可替换 */
public class NoOpCollaborativeFilterScorer implements CollaborativeFilterScorer {

    @Override
    public double score(Long userId, ArticleRecallDTO candidate) {
        return 0;
    }
}
