package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.OrderPaidPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 订单支付成功。消费者：content-service 写 column_purchase（若走最终一致），notification-service 通知专栏作者。
 */
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
