package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单支付成功：columnAuthorId 是通知接收者，userId 是买家。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidPayload implements Serializable {

    private Long orderId;

    private Long userId;

    private Long columnId;

    private Long columnAuthorId;
}
