package com.wisdomhub.content.domain.dto;

import com.wisdomhub.content.constants.QuestionConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 提问。没有 tagIds —— 问题不绑标签（2026-10-09 定，见技术文档 18 问答领域） */
@Data
public class QuestionCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = QuestionConstants.CONTENT_MAX, message = "内容长度不能超过10000")
    private String content;
}
