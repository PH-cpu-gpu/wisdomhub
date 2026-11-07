package com.wisdomhub.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论。逻辑删除；parentId 非空时恒指向一级评论，最多两层。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("comment")
public class Comment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 1-文章 2-回答 3-问题 4-专栏 5-用户 6-标签提案 7-视频 */
    private Byte targetType;

    private Long targetId;

    /** 评论者 ID */
    private Long userId;

    /** 一级评论为 null；二级回复为它的一级父评论 id */
    private Long parentId;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Byte deleted;
}
