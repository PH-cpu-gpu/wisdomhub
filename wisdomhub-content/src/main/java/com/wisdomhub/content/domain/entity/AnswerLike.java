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
 * 回答点赞。关系表 → 物理删除，无 deleted，理由同 {@link ArticleLike}。
 * 表也只有 create_time，实体别多出 update_time
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("answer_like")
public class AnswerLike implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 点赞者 ID */
    private Long userId;

    private Long answerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
