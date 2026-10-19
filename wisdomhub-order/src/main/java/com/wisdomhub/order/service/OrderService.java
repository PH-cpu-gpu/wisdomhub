package com.wisdomhub.order.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.order.domain.dto.OrderCreateDTO;
import com.wisdomhub.order.domain.dto.OrderPayDTO;
import com.wisdomhub.order.domain.dto.RefundDTO;
import com.wisdomhub.order.domain.vo.OrderListItemVO;
import com.wisdomhub.order.domain.vo.OrderVO;

public interface OrderService {

    /** 下单。同用户 + 同商品已有未过期待支付单时复用旧单；创建类本身不幂等 */
    OrderVO createOrder(OrderCreateDTO dto);

    /** 模拟支付：条件更新 0 → 1，成功后发 order.paid */
    OrderVO pay(String orderNo, OrderPayDTO dto);

    /** 取消待支付订单，0 → 2；重复取消幂等 */
    void cancel(String orderNo);

    /** 退款：发起 + 模拟完成/失败/处理中；仅订单买家本人 */
    OrderVO refund(String orderNo, RefundDTO dto);

    /** 订单详情，只能看自己的 */
    OrderVO getOrder(String orderNo);

    /** 我的订单分页，按创建时间倒序 */
    PageResponse<OrderListItemVO> listMyOrders(Integer page, Integer pageSize);
}
