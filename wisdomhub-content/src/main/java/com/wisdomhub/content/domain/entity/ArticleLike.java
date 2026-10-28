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
 * 文章点赞。关系表 → 物理删除，无 deleted：软删的行照样占着 uk_user_article，
 * 「点赞 → 取消 → 再点赞」第三次 INSERT 必撞唯一索引。
 * 表也只有 create_time，实体别多出 update_time，否则 MP 的自动填充会拼出不存在的列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_like")
public class ArticleLike implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 点赞者 ID */
    private Long userId;

    private Long articleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
