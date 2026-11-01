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
 * 标签关系真值。Neo4j 的 Tag→Tag 边由这张表投影。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag_relation")
public class TagRelation implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromTagId;

    private Long toTagId;

    private String relationType;

    /** 1=种子 2=管理员 3=AI 审批 */
    private Integer source;

    /** 来源提案 ID，人工/种子为空 */
    private Long proposalId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
