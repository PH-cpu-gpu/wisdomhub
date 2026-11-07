package com.wisdomhub.search.api.dto;

import lombok.Data;

@Data
public class InteractionCountQueryDTO {

    private Integer targetType;

    private Long targetId;
}
