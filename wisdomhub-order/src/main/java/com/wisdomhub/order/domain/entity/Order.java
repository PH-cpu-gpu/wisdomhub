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

/** 订单。表名是 MySQL 保留字，注解与手写 SQL 都要带反引号 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("`order`")
public class Order implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 给用户看的订单号，yyyyMMddHHmmss + 6 位随机 */
    private String orderNo;

    private Long userId;

    /** 订单金额，单位为分 */
    private Integer amount;

    /** 0 待支付 1 已支付 2 已取消 3 退款中 4 已退款 */
    private Byte state;

    /** 支付截止时间 */
    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
