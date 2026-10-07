package com.wisdomhub.content.domain.entity;

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
 * 评论。实体表 → 逻辑删除，过滤靠手写 .eq(deleted, NOT_DELETED)（项目不用 @TableLogic，见技术文档 7.6）。
 * parentId 非空时恒指向一级评论（写路径归一化保证），所以最多两层。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("comment")
public class Comment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 1-文章 2-回答 */
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
