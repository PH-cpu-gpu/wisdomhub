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
 * 标签/关系提案。pending_key 是生成列，只约束待审行，不在实体里映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag_proposal")
public class TagProposal implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 1=新增标签 2=新增关系 */
    private Integer proposalType;

    /** 0=待审 1=通过 2=拒绝 3=过期 */
    private Integer status;

    private String targetKey;

    /** 提案内容 JSON */
    private String payload;

    /** 触发证据 JSON */
    private String evidence;

    /** 1=AI 2=管理员 3=系统 */
    private Integer proposerType;

    private Long proposerId;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
