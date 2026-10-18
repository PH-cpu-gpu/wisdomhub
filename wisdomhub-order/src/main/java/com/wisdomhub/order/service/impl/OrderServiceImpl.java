package com.wisdomhub.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.mq.event.OrderPaidEvent;
import com.wisdomhub.common.mq.event.payload.OrderPaidPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.order.api.content.ContentFeign;
import com.wisdomhub.order.api.content.dto.ColumnTradeInfoDTO;
import com.wisdomhub.order.config.OrderProperties;
import com.wisdomhub.order.constants.OrderConstants;
import com.wisdomhub.order.domain.dto.OrderCreateDTO;
import com.wisdomhub.order.domain.dto.OrderPayDTO;
import com.wisdomhub.order.domain.entity.Order;
import com.wisdomhub.order.domain.entity.OrderItem;
import com.wisdomhub.order.domain.entity.PaymentRecord;
import com.wisdomhub.order.domain.vo.OrderItemVO;
import com.wisdomhub.order.domain.vo.OrderListItemVO;
import com.wisdomhub.order.domain.vo.OrderVO;
import com.wisdomhub.order.domain.vo.PaymentVO;
import com.wisdomhub.order.mapper.OrderItemMapper;
import com.wisdomhub.order.mapper.OrderMapper;
import com.wisdomhub.order.mapper.PaymentRecordMapper;
import com.wisdomhub.order.service.OrderService;
import com.wisdomhub.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 订单号 / 支付单号撞唯一键时的重试次数 */
    private static final int NO_RETRY = 3;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ContentFeign contentFeign;
    private final EventPublisher eventPublisher;
    private final OrderProperties orderProperties;
    private final PlatformTransactionManager transactionManager;

    // ==================== 下单 ====================

    @Override
    @Transactional
    public OrderVO createOrder(OrderCreateDTO dto) {
        Long userId = CurrentUserUtil.id();
        requireColumnItem(dto.getItemType());

        ColumnTradeInfoDTO column = contentFeign.getTradeInfo(dto.getItemId());
        if (userId.equals(column.getAuthorId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "不能购买自己的专栏");
        }
        if (column.getPrice() == null || column.getPrice() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "免费专栏无需购买");
        }
        if (Boolean.TRUE.equals(contentFeign.hasPurchased(column.getColumnId(), userId))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "已购买该专栏");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Order reusable = orderMapper.selectReusable(userId, dto.getItemType(), dto.getItemId(), now);
        if (reusable != null) {
            return toOrderVO(reusable);
        }

        Order order = insertOrder(userId, column, now);
        orderItemMapper.insert(OrderItem.builder()
                .orderId(order.getId())
                .itemType(dto.getItemType().byteValue())
                .itemId(column.getColumnId())
                .authorId(column.getAuthorId())
                .title(column.getTitle())
                .price(column.getPrice())
                .build());
        return toOrderVO(order);
    }

    private Order insertOrder(Long userId, ColumnTradeInfoDTO column, LocalDateTime now) {
        for (int attempt = 0; attempt < NO_RETRY; attempt++) {
            Order order = Order.builder()
                    .orderNo(OrderNoGenerator.nextOrderNo())
                    .userId(userId)
                    .amount(column.getPrice())
                    .state((byte) OrderConstants.PENDING)
                    .expireTime(now.plusMinutes(orderProperties.getPayTimeoutMinutes()))
                    .build();
            try {
                orderMapper.insert(order);
                return order;
            } catch (DuplicateKeyException e) {
                // 同一秒内 6 位随机撞车，换一个号再来；唯一键只有 uk_order_no
                log.warn("订单号冲突，重试, attempt={}", attempt + 1);
            }
        }
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "订单号生成失败，请重试");
    }

    // ==================== 支付 ====================

    @Override
    @Transactional
    public OrderVO pay(String orderNo, OrderPayDTO dto) {
        Long userId = CurrentUserUtil.id();
        int payType = resolvePayType(dto);
        Order order = requireOwnOrder(orderNo, userId);

        int state = toInt(order.getState());
        if (state == OrderConstants.PAID) {
            // 设置状态类操作幂等：不新增流水，直接回当前结果
            return toOrderVO(order);
        }
        if (state == OrderConstants.CANCELED) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单已取消");
        }
        if (state != OrderConstants.PENDING) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单当前状态不支持支付");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!order.getExpireTime().isAfter(now)) {
            // 惰性过期：只在这里置 2；必须单独提交，否则随后的异常会把这次 UPDATE 回滚
            expireOrderInNewTransaction(order.getId(), now);
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单已过期");
        }

        OrderItem item = requireSingleItem(order.getId());
        if (Boolean.TRUE.equals(contentFeign.hasPurchased(item.getItemId(), userId))) {
            expireOrderInNewTransaction(order.getId(), now);
            throw new BusinessException(CommonErrorCode.CONFLICT, "已购买该专栏");
        }

        insertPayment(order, payType, now);
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.PAID)
                .set(Order::getPaidTime, now)
                // update(null, wrapper) 不触发自动填充，update_time 要自己补
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, order.getId())
                .eq(Order::getState, (byte) OrderConstants.PENDING));
        if (affected == 0) {
            // 并发支付只有一笔能成功；另一笔抛出去回滚刚插入的成功流水
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，支付未生效");
        }

        sendPaidEvent(order, item);
        return toOrderVO(orderMapper.selectById(order.getId()));
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

    private void sendPaidEvent(Order order, OrderItem item) {
        try {
            eventPublisher.send(new OrderPaidEvent(OrderPaidPayload.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .columnId(item.getItemId())
                    .columnAuthorId(item.getAuthorId())
                    .build()));
        } catch (Exception e) {
            // 支付已落库，事件失败只记日志
            log.error("订单支付事件发送失败, orderNo={}", order.getOrderNo(), e);
        }
    }

    // ==================== 取消 ====================

    @Override
    @Transactional
    public void cancel(String orderNo) {
        Long userId = CurrentUserUtil.id();
        Order order = requireOwnOrder(orderNo, userId);

        int state = toInt(order.getState());
        if (state == OrderConstants.CANCELED) {
            return;
        }
        if (state != OrderConstants.PENDING) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "只有待支付订单可以取消");
        }

        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.CANCELED)
                .set(Order::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Order::getId, order.getId())
                .eq(Order::getState, (byte) OrderConstants.PENDING));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，取消失败");
        }
    }

    /** 条件更新 0 → 2，返回影响行数；并发下状态已变则返回 0 */
    private int expireOrder(Long orderId, LocalDateTime now) {
        return orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getState, (byte) OrderConstants.CANCELED)
                .set(Order::getUpdateTime, now)
                .eq(Order::getId, orderId)
                .eq(Order::getState, (byte) OrderConstants.PENDING));
    }

    /** REQUIRES_NEW 单独提交过期状态：调用方随后抛异常，同一事务里会被回滚 */
    private void expireOrderInNewTransaction(Long orderId, LocalDateTime now) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.execute(status -> expireOrder(orderId, now));
    }

    // ==================== 查询 ====================

    @Override
    public OrderVO getOrder(String orderNo) {
        return toOrderVO(requireOwnOrder(orderNo, CurrentUserUtil.id()));
    }

    @Override
    public PageResponse<OrderListItemVO> listMyOrders(Integer page, Integer pageSize) {
        Page<Order> result = orderMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, CurrentUserUtil.id())
                        // 同一秒建的订单会撞 create_time，用 id 兜底
                        .orderByDesc(Order::getCreateTime)
                        .orderByDesc(Order::getId));

        Map<Long, List<OrderItem>> itemsByOrder = loadItems(
                result.getRecords().stream().map(Order::getId).toList());
        List<OrderListItemVO> items = result.getRecords().stream()
                .map(order -> OrderListItemVO.builder()
                        .orderNo(order.getOrderNo())
                        .amount(order.getAmount())
                        .state(toInt(order.getState()))
                        .expireTime(order.getExpireTime())
                        .paidTime(order.getPaidTime())
                        .createTime(order.getCreateTime())
                        .items(itemsByOrder.getOrDefault(order.getId(), List.of()).stream()
                                .map(this::toItemVO).toList())
                        .build())
                .toList();
        return PageUtil.toResponse(result, items);
    }

    // ==================== 共用 ====================

    private void requireColumnItem(Integer itemType) {
        if (itemType == null || itemType != OrderConstants.ITEM_COLUMN) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "暂只支持购买专栏");
        }
    }

    private int resolvePayType(OrderPayDTO dto) {
        int payType = (dto == null || dto.getPayType() == null)
                ? OrderConstants.PAY_TYPE_MOCK : dto.getPayType();
        if (payType != OrderConstants.PAY_TYPE_MOCK) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "暂只支持模拟支付");
        }
        return payType;
    }

    /** 别人的订单当作不存在，不泄露存在性 */
    private Order requireOwnOrder(String orderNo, Long userId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /** 当前一单一商品，取第一条即可 */
    private OrderItem requireSingleItem(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        if (items.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "订单明细缺失");
        }
        return items.get(0);
    }

    private Map<Long, List<OrderItem>> loadItems(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private OrderVO toOrderVO(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        PaymentRecord payment = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, order.getId())
                .eq(PaymentRecord::getState, (byte) OrderConstants.PAYMENT_SUCCESS)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
        return OrderVO.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .state(toInt(order.getState()))
                .expireTime(order.getExpireTime())
                .paidTime(order.getPaidTime())
                .createTime(order.getCreateTime())
                .items(items.stream().map(this::toItemVO).toList())
                .payment(payment == null ? null : PaymentVO.builder()
                        .paymentNo(payment.getPaymentNo())
                        .payType(toInt(payment.getPayType()))
                        .state(toInt(payment.getState()))
                        .paidTime(payment.getPaidTime())
                        .build())
                .build();
    }

    private OrderItemVO toItemVO(OrderItem item) {
        return OrderItemVO.builder()
                .itemType(toInt(item.getItemType()))
                .itemId(item.getItemId())
                .authorId(item.getAuthorId())
                .title(item.getTitle())
                .price(item.getPrice())
                .build();
    }

    private int toInt(Byte value) {
        return value == null ? -1 : value;
    }
}
