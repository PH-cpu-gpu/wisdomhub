package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagRelationDTO {

    @NotNull(message = "起点标签不能为空")
    private Long fromTagId;

    @NotNull(message = "终点标签不能为空")
    private Long toTagId;

    @NotBlank(message = "关系类型不能为空")
    @Size(max = 20, message = "关系类型长度不能超过20")
    private String relationType;
}
