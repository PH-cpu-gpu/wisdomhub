package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 下单用专栏快照：只给 order-service 内部调用，不含简介与封面 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnTradeInfoVO {

    private Long columnId;

    /** 作者 ID，下单时落进 order_item 快照 */
    private Long authorId;

    private String title;

    /** 价格，单位为分 */
    private Integer price;
}
