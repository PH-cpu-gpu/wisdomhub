package com.wisdomhub.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 订单明细。title / price / authorId 是下单时的快照，不回查 content */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemVO {

    private Integer itemType;

    private Long itemId;

    private Long authorId;

    private String title;

    /** 成交单价，单位为分 */
    private Integer price;
}
