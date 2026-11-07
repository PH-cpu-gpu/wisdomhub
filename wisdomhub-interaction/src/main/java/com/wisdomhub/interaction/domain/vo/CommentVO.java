package com.wisdomhub.interaction.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 一级评论的 replies 非空，二级回复的 replies 恒为空列表。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long commentId;

    private Long userId;

    /** 一级评论为 null；二级回复为它的一级父评论 id */
    private Long parentId;

    private String content;

    private LocalDateTime createTime;

    private List<CommentVO> replies;
}
