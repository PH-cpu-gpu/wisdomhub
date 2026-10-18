package com.wisdomhub.recommendation.domain.vo;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 打分结果，带分量明细供排查 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoredArticleVO {

    private ArticleRecallDTO article;

    private double totalScore;

    private double interestScore;

    private double hotScore;

    private double freshnessScore;

    private double cfScore;
}
