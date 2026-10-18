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
}
