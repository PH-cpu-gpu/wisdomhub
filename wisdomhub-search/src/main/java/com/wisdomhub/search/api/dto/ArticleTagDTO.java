package com.wisdomhub.search.api.dto;

import lombok.Data;

/** 对应 content-service 的 ArticleTagVO */
@Data
public class ArticleTagDTO {

    private Long tagId;

    private String name;

    private String category;
}
