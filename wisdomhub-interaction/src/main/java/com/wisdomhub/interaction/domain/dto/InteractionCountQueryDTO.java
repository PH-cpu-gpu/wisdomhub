package com.wisdomhub.interaction.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InteractionCountQueryDTO {

    @NotNull(message = "targetType 不能为空")
    private Integer targetType;

    @NotNull(message = "targetId 不能为空")
    private Long targetId;
}
