package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应 content-service 的 QuestionSyncVO。deleted=1 的行也要处理（增量发 question.deleted）。
 */
@Data
public class QuestionSyncDTO {

    private Long questionId;

    private Long userId;

    private String title;

    private String content;

    private Integer answerCount;

    private Integer favoriteCount;

    private Long acceptedAnswerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
