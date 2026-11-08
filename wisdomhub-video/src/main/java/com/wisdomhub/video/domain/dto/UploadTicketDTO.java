package com.wisdomhub.video.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UploadTicketDTO {

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    @NotBlank(message = "MIME 类型不能为空")
    @Size(max = 64, message = "MIME 类型过长")
    private String mimeType;
}
