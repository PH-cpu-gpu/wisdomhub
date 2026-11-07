package com.wisdomhub.recommendation.api.interaction.dto;

import lombok.Data;

@Data
public class InteractionCountQueryDTO {

    private Integer targetType;

    private Long targetId;
}
