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

/** 专栏购买记录：内容鉴权凭据，退款物理删除 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("column_purchase")
public class ColumnPurchase implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long columnId;

    private Long orderId;

    private Integer price;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
