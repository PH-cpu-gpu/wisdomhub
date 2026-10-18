package com.wisdomhub.recommendation.api.content.dto;

import lombok.Data;

/** 对应 content 的 ArticleBriefVO */
@Data
public class ArticleBriefDTO {

    private Long articleId;

    private String title;

    private String summary;
}
