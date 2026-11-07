package com.wisdomhub.interaction.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateDTO {

    /** 1-文章 2-回答 3-问题；为空按 1-文章，其他值 → 400 */
    private Integer targetType;

    @NotNull(message = "评论目标不能为空")
    private Long targetId;

    /** 回复目标。一级评论为 null；回复二级评论时服务端归一到它的一级父评论 */
    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容长度不能超过1000")
    private String content;
}
