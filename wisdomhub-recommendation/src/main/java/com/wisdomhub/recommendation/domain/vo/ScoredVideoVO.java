package com.wisdomhub.recommendation.domain.vo;

import com.wisdomhub.recommendation.api.search.dto.VideoRecallDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 视频打分结果，带分量明细供排查。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoredVideoVO {

    private VideoRecallDTO video;

    private double totalScore;

    private double interestScore;

    private double hotScore;

    private double freshnessScore;
}
