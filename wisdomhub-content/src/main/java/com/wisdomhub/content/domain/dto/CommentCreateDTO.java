package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateDTO {

    /** 1-文章（缺省即按文章）。传 2 或别的值 → 400 */
    private Integer targetType;

    @NotNull(message = "评论目标不能为空")
    private Long targetId;

    /**
     * 回复目标。一级评论 id = 评论该文章；二级评论 id = 回复那条回复，
     * 服务端会归一到它的一级父评论。为空 = 直接评论文章（一级评论）
     */
    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容长度不能超过1000")
    private String content;
}
