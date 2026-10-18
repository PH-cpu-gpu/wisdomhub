package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 对应 search 的 ArticleRecallVO；打分要用的标签与计数都在这里，不再逐篇回调 content */
@Data
public class ArticleRecallDTO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    private List<TagBriefDTO> tags;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
