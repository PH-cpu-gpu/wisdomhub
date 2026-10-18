package com.wisdomhub.order.api.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** content-service 返回的下单快照 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnTradeInfoDTO {

    private Long columnId;

    private Long authorId;

    private String title;

    /** 价格，单位为分 */
    private Integer price;
}
