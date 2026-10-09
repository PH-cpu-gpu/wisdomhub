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
 * 专栏。表名是 MySQL 保留字，`@TableName` 的反引号会原样拼进 SQL，一处注解就够（技术文档 7.6）；
 * 手写 SQL 里出现 `FROM column` 也要自己补。表名不改：column_article / column_purchase 都以它为前缀。
 * 逻辑删除，过滤靠手写 .eq(deleted, NOT_DELETED)（项目不用 @TableLogic）。
 * article_count 是快照列，只由归入/移出原子 UPDATE，不 bump update_time（同 B1.26 口径）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("`column`")
public class Column implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建者 ID */
    private Long userId;

    private String title;

    private String description;

    /** 封面 URL，可空 */
    private String cover;

    /** 价格，单位为分，0 表示免费。B1.27 只存不判，购买流程归 B2.23~B2.25 */
    private Integer price;

    /** 收录文章数，快照列 */
    private Integer articleCount;

    /** 1-正常 2-下架 */
    private Byte state;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Byte deleted;
}
