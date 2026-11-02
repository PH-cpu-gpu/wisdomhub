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
 * 未收录技术实体汇总。extract_count 按「不同文章」累加，明细去重在 unknown_entity_hit；
 * query_count 是旧查询来源的遗留列，已停止写入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("unknown_entity")
public class UnknownEntity implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 最近一次出现的原始写法 */
    private String name;

    /** 归一化名，uk_name_norm 唯一；与 tag.name_norm 同一套规则 */
    private String nameNorm;

    /** 被抽取命中的不同文章数 */
    private Integer extractCount;

    /** 历史查询次数，已停止采集，恒为新行的 0 */
    private Integer queryCount;

    /** 最近一条样本文章 */
    private Long sampleArticleId;

    /** 已升级成的提案 ID；为空表示还没提过 */
    private Long promotedProposalId;

    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
