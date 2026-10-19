package com.wisdomhub.order.api.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 支付成功后通知 content 写入购买记录的请求体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnPurchaseDTO {

    private Long orderId;

    /** 成交价快照，单位为分 */
    private Integer price;
}
