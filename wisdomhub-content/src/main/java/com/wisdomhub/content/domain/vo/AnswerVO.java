package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回答。正文全量返回（回答不是长文，同评论的口径）。
 * 不给 liked 布尔：列表一页 N 条就是 N 次查询（同 B1.26 的判据）；
 * 是否已采纳由 QuestionDetailVO.acceptedAnswerId 提供，不在每条上重复一份
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerVO {

    private Long answerId;

    private Long questionId;

    private Long userId;

    private String content;

    private Integer likeCount;

    /** B1.28 无写入方（评论回答未做），恒为 0 */
    private Integer commentCount;

    private LocalDateTime createTime;
}
