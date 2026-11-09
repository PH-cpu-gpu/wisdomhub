package com.wisdomhub.video.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 视频编辑：标题、简介、标签全量替换；资产与播放地址不可改。 */
@Data
public class VideoUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @Size(max = 1000, message = "简介不能超过1000个字符")
    private String summary;

    @Size(max = 10, message = "一个视频最多选择10个标签")
    private List<Long> tagIds;
}
