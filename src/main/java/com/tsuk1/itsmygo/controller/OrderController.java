package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端订单：下单、支付、取消、确认收货 —— 全部只操作自己的订单 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 领一次性提交令牌 —— 前端进结算页时调，下单时带上。
     * 用户连点两次「下单」只有第一次会成功，第二次因令牌已消费被拒。
     */
    @GetMapping("/token")
    public Result<String> token(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(orderService.issueToken(user.id()));
    }

    @PostMapping
    public Result<OrderView> create(@AuthenticationPrincipal CurrentUser user,
                                    @Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.create(user.id(), request));
    }

    @GetMapping
    public Result<PageResponse<Order>> page(@AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageForCustomer(user.id(), page, size));
    }

    @GetMapping("/{id}")
    public Result<OrderView> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(orderService.detailForCustomer(user.id(), id));
    }

    /** 模拟支付：真实项目这里会调支付网关并等异步回调 */
    @PostMapping("/{id}/pay")
    public Result<OrderView> pay(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(orderService.pay(user.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<OrderView> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(orderService.cancel(user.id(), id));
    }

    @PostMapping("/{id}/confirm")
    public Result<OrderView> confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(orderService.confirmReceipt(user.id(), id));
    }
}
