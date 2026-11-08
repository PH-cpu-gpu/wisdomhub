package com.wisdomhub.video.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VideoPublishDTO {

    @NotNull(message = "媒体资产不能为空")
    private Long assetId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @Size(max = 1000, message = "简介不能超过1000个字符")
    private String summary;
}
