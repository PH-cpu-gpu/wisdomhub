package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 问题列表项。**不返回 content** —— 正文是 TEXT，跟着列表回传会让一页变成几十 KB；
 * 列表只给标题，点进详情才拿正文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionListItemVO {

    private Long questionId;

    private Long userId;

    private String title;

    private Integer answerCount;

    private Integer favoriteCount;

    /** 是否已采纳答案，由 accepted_answer_id 派生。列表页画「已解决」标记用 */
    private Boolean solved;

    private LocalDateTime createTime;
}
