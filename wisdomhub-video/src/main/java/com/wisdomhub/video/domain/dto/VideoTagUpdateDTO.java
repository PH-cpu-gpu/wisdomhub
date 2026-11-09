package com.wisdomhub.video.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 全量替换视频标签：传空列表即清空 */
@Data
public class VideoTagUpdateDTO {

    @NotNull(message = "标签列表不能为空，清空请传空数组")
    @Size(max = 10, message = "一个视频最多选择10个标签")
    private List<Long> tagIds;
}
