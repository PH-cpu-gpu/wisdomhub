package com.wisdomhub.order.domain.entity;

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

/** 订单明细。title / price / authorId 是下单快照，订单读路径不回查 content */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_item")
public class OrderItem implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    /** 商品类型：1 专栏 */
    private Byte itemType;

    private Long itemId;

    /** 作者 ID 快照，支付事件通知用 */
    private Long authorId;

    private String title;

    /** 成交单价，单位为分 */
    private Integer price;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
