package com.wisdomhub.recommendation.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 推荐卡片，不暴露内部打分 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationItemVO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    private List<RecommendationTagVO> tags;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
