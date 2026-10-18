package com.wisdomhub.recommendation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 用户兴趣画像 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestProfileVO {

    private List<TagWeightVO> tags;
}
