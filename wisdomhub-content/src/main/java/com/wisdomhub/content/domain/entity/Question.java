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
 * 问题。正文留在 MySQL（TEXT），不写 MongoDB —— 问答是几十到几百字，
 * 不为它开一次跨存储查询（技术文档 7.6）。
 * 逻辑删除，过滤靠手写 .eq(deleted, NOT_DELETED)（项目不用 @TableLogic）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("question")
public class Question implements Serializable {

    /** 雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 提问者 ID */
    private Long userId;

    private String title;

    private String content;

    /** 回答数，快照列，只由发表/删除回答原子 UPDATE */
    private Integer answerCount;

    /** 收藏数，快照列 */
    private Integer favoriteCount;

    /**
     * 采纳的回答 ID，未采纳为 NULL。只有一列 → 采纳是可以改的（PUT 覆盖），
     * 不是「只能采纳一次」；取消采纳就是置回 NULL
     */
    private Long acceptedAnswerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Byte deleted;
}
