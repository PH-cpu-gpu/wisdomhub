package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ColumnPurchaseDTO {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    /** 成交价快照，单位为分，来自订单明细 */
    @NotNull(message = "成交价不能为空")
    @Min(value = 0, message = "成交价不能为负")
    private Integer price;
}
