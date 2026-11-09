package com.wisdomhub.recommendation.service;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.api.search.dto.VideoRecallDTO;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.ScoredArticleVO;
import com.wisdomhub.recommendation.domain.vo.ScoredVideoVO;

import java.util.List;

public interface RecommendationScorer {

    /** 兴趣 + 热度 + 时效 + 协同过滤加权，返回按总分降序的候选 */
    List<ScoredArticleVO> score(List<ArticleRecallDTO> candidates, Long userId, InterestProfileVO profile);

    /** 视频没有协同过滤，其余分量与文章同公式 */
    List<ScoredVideoVO> scoreVideos(List<VideoRecallDTO> candidates, InterestProfileVO profile);
}
