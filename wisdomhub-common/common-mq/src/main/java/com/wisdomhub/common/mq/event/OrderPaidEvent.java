package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.OrderPaidPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 订单支付成功。B2.24 起购买权限由 Seata 同步写，本事件只供 notification-service 通知专栏作者 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OrderPaidEvent extends BaseEvent<OrderPaidPayload> {

    public OrderPaidEvent(OrderPaidPayload payload) {
        super(MqRoutingKeyConstants.ORDER_PAID, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ORDER_PAID;
    }
}
