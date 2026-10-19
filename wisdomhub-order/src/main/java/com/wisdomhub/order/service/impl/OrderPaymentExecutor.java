package com.wisdomhub.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.order.api.content.ContentFeign;
import com.wisdomhub.order.api.content.dto.ColumnPurchaseDTO;
import com.wisdomhub.order.constants.OrderConstants;
import com.wisdomhub.order.domain.entity.Order;
import com.wisdomhub.order.domain.entity.OrderItem;
import com.wisdomhub.order.domain.entity.PaymentRecord;
import com.wisdomhub.order.mapper.OrderMapper;
import com.wisdomhub.order.mapper.PaymentRecordMapper;
import com.wisdomhub.order.util.OrderNoGenerator;
import org.apache.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 支付与开通权限的全局事务边界：order 本地写单 + content 写 column_purchase */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentExecutor {

    private static final int NO_RETRY = 3;

    private final OrderMapper orderMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContentFeign contentFeign;

    /**
     * 独立 bean 是为了走代理：OrderServiceImpl 调到这里时 @GlobalTransactional 才生效。
     */
    @GlobalTransactional
    @Transactional
    public void payAndOpen(Order order, OrderItem item, int payType, LocalDateTime now) {
        insertPayment(order, payType, now);
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.PAID)
                .set(Order::getPaidTime, now)
                // update(null, wrapper) 不触发自动填充，update_time 要自己补
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, order.getId())
                .eq(Order::getState, (byte) OrderConstants.PENDING)
                .gt(Order::getExpireTime, now));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，支付未生效");
        }
        contentFeign.purchase(item.getItemId(), ColumnPurchaseDTO.builder()
                .orderId(order.getId())
                .price(item.getPrice())
                .build());
    }

    private void insertPayment(Order order, int payType, LocalDateTime now) {
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            PaymentRecord payment = PaymentRecord.builder()
                    .paymentNo(OrderNoGenerator.nextPaymentNo())
                    .orderId(order.getId())
                    .amount(order.getAmount())
                    .payType((byte) payType)
                    .state((byte) OrderConstants.PAYMENT_SUCCESS)
                    // 模拟支付没有真实渠道：transaction_id / refund_no 保持 NULL，不能写空串
                    .expireTime(order.getExpireTime())
                    .paidTime(now)
                    .refundAmount(0)
                    .refundState((byte) OrderConstants.REFUND_NONE)
                    .build();
            try {
                paymentRecordMapper.insert(payment);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("支付单号冲突，重试, attempt={}", attempt + 1);
            }
        }
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "支付单号生成失败，请重试");
    }
}
