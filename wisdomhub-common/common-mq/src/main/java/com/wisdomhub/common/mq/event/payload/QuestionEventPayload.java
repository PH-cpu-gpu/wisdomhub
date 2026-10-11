package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问题事件的内容快照 = `question` 表那一行。正文是 TEXT（几十到几百字，在 MySQL 里），
 * 不像文章正文那样是几十 KB 的大字段，所以直接跟着事件走，消费方不必回查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionEventPayload implements Serializable {

    private Long questionId;

    private Long userId;

    private String title;

    private String content;

    private Integer answerCount;

    private Integer favoriteCount;

    private Long acceptedAnswerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
