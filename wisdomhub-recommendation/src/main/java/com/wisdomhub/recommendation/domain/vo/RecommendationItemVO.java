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

    /** 1-文章 7-视频，取值同 ContentTargetTypeConstants */
    private Integer contentType;

    private Long articleId;

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private List<RecommendationTagVO> tags;

    /** 视频封面，文章为空 */
    private String coverKey;

    private String coverUrl;

    /** 视频时长（毫秒），文章为空 */
    private Long durationMs;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
