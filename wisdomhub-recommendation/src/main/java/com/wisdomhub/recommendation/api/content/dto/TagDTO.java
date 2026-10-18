package com.wisdomhub.recommendation.api.content.dto;

import lombok.Data;

/** 对应 content 的 TagVO */
@Data
public class TagDTO {

    private Long tagId;

    private String name;

    private String category;

    private String description;
}
