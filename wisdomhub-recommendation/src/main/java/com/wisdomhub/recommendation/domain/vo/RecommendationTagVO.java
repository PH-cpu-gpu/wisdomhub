package com.wisdomhub.recommendation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationTagVO {

    private Long tagId;

    private String name;

    private String category;
}
