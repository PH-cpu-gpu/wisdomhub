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
 * 专栏文章。关系表 → 物理删除，无 deleted，理由同 {@link ArticleLike}：
 * 软删的行照样占着 uk_column_article，「归入 → 移出 → 再归入」第三次 INSERT 必撞唯一索引。
 * 表也只有 create_time，实体别多出 update_time。专栏内文章按本表的 create_time 升序（章节顺序）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("column_article")
public class ColumnArticle implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long columnId;

    private Long articleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
