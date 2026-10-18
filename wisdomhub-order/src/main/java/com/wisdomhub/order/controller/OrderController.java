package com.wisdomhub.order.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.order.domain.dto.OrderCreateDTO;
import com.wisdomhub.order.domain.dto.OrderPayDTO;
import com.wisdomhub.order.domain.vo.OrderListItemVO;
import com.wisdomhub.order.domain.vo.OrderVO;
import com.wisdomhub.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单controller")
@Validated
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping
    public R<OrderVO> create(@Valid @RequestBody OrderCreateDTO orderCreateDTO) {
        return R.success(orderService.createOrder(orderCreateDTO));
    }

    @Operation(summary = "模拟支付")
    @PostMapping("/{orderNo}/pay")
    public R<OrderVO> pay(@PathVariable String orderNo,
                          @RequestBody(required = false) OrderPayDTO orderPayDTO) {
        return R.success(orderService.pay(orderNo, orderPayDTO));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderNo}/cancel")
    public R<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(orderNo);
        return R.success();
    }

    @Operation(summary = "我的订单")
    @GetMapping("/mine")
    public R<PageResponse<OrderListItemVO>> mine(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(orderService.listMyOrders(page, pageSize));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public R<OrderVO> detail(@PathVariable String orderNo) {
        return R.success(orderService.getOrder(orderNo));
    }
}
