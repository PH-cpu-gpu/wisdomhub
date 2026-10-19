package com.wisdomhub.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.order.api.content.ContentFeign;
import com.wisdomhub.order.constants.OrderConstants;
import com.wisdomhub.order.domain.entity.Order;
import com.wisdomhub.order.domain.entity.PaymentRecord;
import com.wisdomhub.order.mapper.OrderMapper;
import com.wisdomhub.order.mapper.PaymentRecordMapper;
import org.apache.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 退款完成与收回权限的全局事务边界：order 改状态 + content 删购买记录 */
@Service
@RequiredArgsConstructor
public class RefundExecutor {

    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContentFeign contentFeign;

    @GlobalTransactional
    @Transactional
    public void complete(Long orderId, Long paymentId, Long columnId, Long userId, LocalDateTime now) {
        int orderAffected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.REFUNDED)
                .set(Order::getRefundTime, now)
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, orderId)
                .eq(Order::getState, (byte) OrderConstants.REFUNDING));
        if (orderAffected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，退款未完成");
        }

        int payAffected = paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .set(PaymentRecord::getRefundState, (byte) OrderConstants.REFUND_SUCCESS)
                .set(PaymentRecord::getRefundTime, now)
                .set(PaymentRecord::getUpdateTime, now)
                .eq(PaymentRecord::getId, paymentId)
                .eq(PaymentRecord::getRefundState, (byte) OrderConstants.REFUND_PROCESSING));
        if (payAffected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "支付流水状态已变化，退款未完成");
        }

        contentFeign.refundPurchase(columnId, userId, orderId);
    }
}
