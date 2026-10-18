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

/** 支付流水。一个订单可多次尝试；模拟支付的 transaction_id / refund_no 必须为 NULL */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment_record")
public class PaymentRecord implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 支付单号，渠道 out_trade_no */
    private String paymentNo;

    private Long orderId;

    /** 支付金额，单位为分 */
    private Integer amount;

    /** 1 模拟支付 2 微信支付 3 支付宝支付 */
    private Byte payType;

    /** 0 待支付 1 成功 2 失败 3 已关闭 */
    private Byte state;

    /** 微信 prepay_id，其他渠道为 NULL */
    private String prepayId;

    /** 渠道交易号，支付成功后由渠道返回；模拟支付恒为 NULL */
    private String transactionId;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime closeTime;

    private String refundNo;

    /** 退款金额，单位为分 */
    private Integer refundAmount;

    /** 0 无 1 处理中 2 成功 3 失败 */
    private Byte refundState;

    private String channelRefundId;

    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
