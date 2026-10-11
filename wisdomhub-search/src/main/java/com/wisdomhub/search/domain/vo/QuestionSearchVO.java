package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** 问题搜索结果项。问题没有标签（库里就没有 question_tag），所以比文章少一个 tags */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSearchVO {

    private Long questionId;

    private Long userId;

    private String title;

    private String content;

    /** key 取 title / content */
    private Map<String, String> highlight;

    private Integer answerCount;

    private Integer favoriteCount;

    private Long acceptedAnswerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
