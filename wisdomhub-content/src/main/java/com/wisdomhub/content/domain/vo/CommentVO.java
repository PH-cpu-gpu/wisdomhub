package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论。一级评论的 replies 非空（二级回复，时间正序）；二级回复的 replies 恒为空列表。
 * userId 只给 ID，昵称头像由前端另调 user-service（与 ArticleDetailVO 同口径）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long commentId;

    private Long userId;

    /** 一级评论为 null；二级回复为它的一级父评论 id（发布时已归一化） */
    private Long parentId;

    private String content;

    private LocalDateTime createTime;

    /** 空集合给 [] 不给 null（17.3） */
    private List<CommentVO> replies;
}
