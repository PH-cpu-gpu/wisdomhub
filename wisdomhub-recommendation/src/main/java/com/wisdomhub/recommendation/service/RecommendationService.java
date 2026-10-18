package com.wisdomhub.recommendation.service;

import com.wisdomhub.recommendation.domain.vo.RecommendationItemVO;

import java.util.List;

public interface RecommendationService {

    /** 首页推荐；userId 为空走热门兜底 */
    List<RecommendationItemVO> home(Long userId, Integer limit);

    /** 相关文章；userId 只影响排序 */
    List<RecommendationItemVO> related(Long articleId, Long userId, Integer limit);

    /** 热门文章，保持召回热度序 */
    List<RecommendationItemVO> hot(Long tagId, Integer limit);
}
