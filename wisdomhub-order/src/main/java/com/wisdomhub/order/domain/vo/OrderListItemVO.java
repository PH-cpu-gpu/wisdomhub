package com.wisdomhub.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 我的订单列表项。明细一次批量查回，避免 N+1 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListItemVO {

    private String orderNo;

    private Integer amount;

    private Integer state;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime createTime;

    private List<OrderItemVO> items;
}
