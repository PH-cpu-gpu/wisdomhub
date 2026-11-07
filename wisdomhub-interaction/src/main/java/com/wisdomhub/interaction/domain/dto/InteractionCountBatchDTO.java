package com.wisdomhub.interaction.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class InteractionCountBatchDTO {

    @NotNull(message = "批量查询不能为空")
    @Size(max = 100, message = "批量查询最多100条")
    @Valid
    private List<InteractionCountQueryDTO> items;
}
