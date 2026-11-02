package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 文章抽取时被丢弃的实体上报 */
@Data
public class UnknownEntityHitDTO {

    @NotNull(message = "文章 ID 不能为空")
    private Long articleId;

    @NotEmpty(message = "实体名不能为空")
    @Size(max = 100, message = "一次最多上报100个实体")
    private List<String> names;
}
