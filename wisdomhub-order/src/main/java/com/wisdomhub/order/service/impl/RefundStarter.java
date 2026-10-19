package com.wisdomhub.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.order.constants.OrderConstants;
import com.wisdomhub.order.domain.entity.Order;
import com.wisdomhub.order.domain.entity.PaymentRecord;
import com.wisdomhub.order.mapper.OrderMapper;
import com.wisdomhub.order.mapper.PaymentRecordMapper;
import com.wisdomhub.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 退款发起与失败处理：本地事务，先于全局事务提交，保证“退款中”状态能留下来 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundStarter {

    private static final int NO_RETRY = 3;

    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    @Transactional
    public void start(Long orderId, Long paymentId, Integer amount, LocalDateTime now) {
        int orderAffected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.REFUNDING)
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, orderId)
                .eq(Order::getState, (byte) OrderConstants.PAID));
        if (orderAffected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，退款未发起");
        }

        if (startPaymentRefund(paymentId, amount, now) == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "支付流水状态已变化，退款未发起");
        }
    }

    @Transactional
    public void fail(Long orderId, Long paymentId, LocalDateTime now) {
        int orderAffected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.PAID)
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, orderId)
                .eq(Order::getState, (byte) OrderConstants.REFUNDING));
        if (orderAffected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，退款失败处理未生效");
        }
        int payAffected = paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .set(PaymentRecord::getRefundState, (byte) OrderConstants.REFUND_FAILED)
                .set(PaymentRecord::getUpdateTime, now)
                .eq(PaymentRecord::getId, paymentId)
                .eq(PaymentRecord::getRefundState, (byte) OrderConstants.REFUND_PROCESSING));
        if (payAffected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "支付流水状态已变化，退款失败处理未生效");
        }
    }

    private int startPaymentRefund(Long paymentId, Integer amount, LocalDateTime now) {
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            try {
                return paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                        .set(PaymentRecord::getRefundNo, OrderNoGenerator.nextRefundNo())
                        .set(PaymentRecord::getRefundAmount, amount)
                        .set(PaymentRecord::getRefundState, (byte) OrderConstants.REFUND_PROCESSING)
                        .set(PaymentRecord::getUpdateTime, now)
                        .eq(PaymentRecord::getId, paymentId)
                        .eq(PaymentRecord::getState, (byte) OrderConstants.PAYMENT_SUCCESS)
                        .in(PaymentRecord::getRefundState,
                                OrderConstants.REFUND_NONE, OrderConstants.REFUND_FAILED));
            } catch (DuplicateKeyException e) {
                log.warn("退款单号冲突，重试, attempt={}", attempt + 1);
            }
        }
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "退款单号生成失败，请重试");
    }
}
