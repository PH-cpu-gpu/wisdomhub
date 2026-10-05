package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增 / 修改标签共用 */
@Data
public class TagDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名长度不能超过50")
    private String name;

    @Size(max = 255, message = "标签描述长度不能超过255")
    private String description;
}
