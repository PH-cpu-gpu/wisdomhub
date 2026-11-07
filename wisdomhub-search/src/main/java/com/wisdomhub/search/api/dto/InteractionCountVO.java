package com.wisdomhub.search.api.dto;

import lombok.Data;

@Data
public class InteractionCountVO {

    private Integer targetType;

    private Long targetId;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
