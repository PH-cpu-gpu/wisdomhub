package com.wisdomhub.order.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateDTO {

    /** 商品类型：1 专栏。*/
    @NotNull(message = "商品类型不能为空")
    private Integer itemType;

    @NotNull(message = "商品 ID 不能为空")
    private Long itemId;
}
