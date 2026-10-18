package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

/** 对应 search 的 TagBriefVO；tagId 是数值型 */
@Data
public class TagBriefDTO {

    private Long tagId;

    private String name;

    private String category;
}
