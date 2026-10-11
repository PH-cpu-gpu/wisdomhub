package com.wisdomhub.search.domain.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * question_index 的写入文档，字段与 resources/es/question_index.json 逐一对齐。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionIndexDoc {

    private Long questionId;

    private String title;

    /** completion 字段，写入时显式带上，取 title */
    private String suggest;

    private String content;

    private Long userId;

    private Integer answerCount;

    private Integer favoriteCount;

    private Long acceptedAnswerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
