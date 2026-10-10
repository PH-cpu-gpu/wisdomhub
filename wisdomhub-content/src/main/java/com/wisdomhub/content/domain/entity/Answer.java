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
 * 回答。正文同 {@link Question} 留在 MySQL。
 * comment_count 列存在但 B1.28 没有写入方 —— 评论回答（comment.target_type=2）还没做
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("answer")
public class Answer implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long questionId;

    /** 回答者 ID */
    private Long userId;

    private String content;

    /** 点赞数，快照列 */
    private Integer likeCount;

    /** 评论数，快照列。B1.28 无写入方，见类注释 */
    private Integer commentCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Byte deleted;
}
