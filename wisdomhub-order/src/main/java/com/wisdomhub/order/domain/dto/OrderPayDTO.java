package com.wisdomhub.order.domain.dto;

import lombok.Data;

@Data
public class OrderPayDTO {

    /** 支付方式，缺省 1-模拟支付；2/3 尚未接入 */
    private Integer payType;
}
