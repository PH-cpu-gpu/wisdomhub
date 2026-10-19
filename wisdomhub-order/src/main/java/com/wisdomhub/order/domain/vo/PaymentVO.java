package com.wisdomhub.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 订单最近一笔成功支付，用于支付结果页展示 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVO {

    private String paymentNo;

    private Integer payType;

    private Integer state;

    private LocalDateTime paidTime;

    private String refundNo;

    /** 退款金额，单位为分 */
    private Integer refundAmount;

    /** 0 无 1 处理中 2 成功 3 失败 */
    private Integer refundState;

    private LocalDateTime refundTime;
}
