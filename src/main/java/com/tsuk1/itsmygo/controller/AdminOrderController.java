package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端订单：查看全部订单、发货 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Result<PageResponse<Order>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long merchantId) {
        return Result.ok(orderService.pageForAdmin(page, size, status, merchantId));
    }

    @GetMapping("/{id}")
    public Result<OrderView> detail(@PathVariable Long id) {
        return Result.ok(orderService.detailForAdmin(id));
    }

    @PostMapping("/{id}/ship")
    public Result<OrderView> ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request) {
        return Result.ok(orderService.ship(id, request));
    }

    /** 同意退款：状态转已退款、回滚库存、支付流水同步 */
    @PostMapping("/{id}/refund/approve")
    public Result<OrderView> approveRefund(@PathVariable Long id) {
        return Result.ok(orderService.approveRefund(id));
    }

    /** 驳回退款：回到申请前的状态 */
    @PostMapping("/{id}/refund/reject")
    public Result<OrderView> rejectRefund(@PathVariable Long id) {
        return Result.ok(orderService.rejectRefund(id));
    }

    /**
     * 取消超时未支付的订单。
     * 现在是手动触发（自己测试、或将来挂定时任务时调它）；
     * 真要自动化就加个 @Scheduled，注意多实例下得配分布式锁。
     */
    @PostMapping("/cancel-expired")
    public Result<Integer> cancelExpired(@RequestParam(defaultValue = "30") int minutes) {
        return Result.ok(orderService.cancelExpired(minutes));
    }
}
