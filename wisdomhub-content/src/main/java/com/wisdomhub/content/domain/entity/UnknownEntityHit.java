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
 * 未收录实体命中明细。(name_norm, source, ref_key) 唯一，重复上报只留一行，
 * 汇总表的计数只在明细真正新增时才累加。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("unknown_entity_hit")
public class UnknownEntityHit implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String nameNorm;

    /** 1=抽取；2=查询已停止写入 */
    private Integer source;

    /** articleId */
    private String refKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
