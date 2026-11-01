package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagAliasDTO {

    @NotBlank(message = "别名不能为空")
    @Size(max = 50, message = "别名长度不能超过50")
    private String alias;
}
