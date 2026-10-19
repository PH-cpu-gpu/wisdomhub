package com.wisdomhub.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 订单详情。创建、支付、详情三个接口共用 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    private String orderNo;

    /** 订单金额，单位为分 */
    private Integer amount;

    /** 0 待支付 1 已支付 2 已取消 3 退款中 4 已退款 */
    private Integer state;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime refundTime;

    private LocalDateTime createTime;

    private List<OrderItemVO> items;

    /** 最近一笔成功支付，未支付时为 null */
    private PaymentVO payment;
}
