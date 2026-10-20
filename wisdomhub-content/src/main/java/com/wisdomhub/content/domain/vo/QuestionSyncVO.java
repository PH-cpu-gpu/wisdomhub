package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 问题对账快照，供 search-service 修 question_index。deleted 要带上，增量才能发 question.deleted。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSyncVO {

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
