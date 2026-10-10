package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 问题详情。采纳的那条单独给一份完整回答（acceptedAnswer），
 * 不让前端从分页列表里捞 —— 它可能不在第一页
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDetailVO {

    private Long questionId;

    private Long userId;

    private String title;

    private String content;

    private Integer answerCount;

    private Integer favoriteCount;

    /** 未采纳为 null */
    private Long acceptedAnswerId;

    /** 已采纳时是被采纳的回答，否则 null */
    private AnswerVO acceptedAnswer;

    /** 当前登录用户是否已收藏。详情是可选登录路径，游客恒 false（同 B1.26） */
    private Boolean favorited;

    private LocalDateTime createTime;
}
