package com.wisdomhub.recommendation.service;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.ScoredArticleVO;

import java.util.List;

public interface RecommendationScorer {

    /** 兴趣 + 热度 + 时效 + 协同过滤加权，返回按总分降序的候选 */
    List<ScoredArticleVO> score(List<ArticleRecallDTO> candidates, Long userId, InterestProfileVO profile);
}
