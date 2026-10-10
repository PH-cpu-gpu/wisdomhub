package com.wisdomhub.content.domain.dto;

import com.wisdomhub.content.constants.QuestionConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerCreateDTO {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = QuestionConstants.CONTENT_MAX, message = "回答长度不能超过10000")
    private String content;
}
