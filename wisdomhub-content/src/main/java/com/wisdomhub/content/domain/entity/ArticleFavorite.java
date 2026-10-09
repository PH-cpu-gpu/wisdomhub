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
 * 文章收藏。关系表 → 物理删除，无 deleted，理由同 {@link ArticleLike}。
 * 「我的收藏」列表按本表的 create_time 倒序（收藏顺序，不是文章发布顺序）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_favorite")
public class ArticleFavorite implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 收藏者 ID */
    private Long userId;

    private Long articleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
