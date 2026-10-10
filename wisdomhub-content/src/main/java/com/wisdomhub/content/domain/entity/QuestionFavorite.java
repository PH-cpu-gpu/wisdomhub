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
 * 问题收藏。关系表 → 物理删除，无 deleted，理由同 {@link ArticleLike}。
 * 「我的问题收藏」按本表的 create_time 倒序（收藏顺序，不是提问顺序）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("question_favorite")
public class QuestionFavorite implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 收藏者 ID */
    private Long userId;

    private Long questionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
