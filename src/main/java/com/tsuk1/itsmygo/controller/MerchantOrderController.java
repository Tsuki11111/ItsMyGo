package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
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

/** 商家后台订单：只看/只操作本店订单 */
@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

    private final OrderService orderService;

    public MerchantOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Result<PageResponse<Order>> page(@AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String status) {
        return Result.ok(orderService.pageForMerchant(requireMerchant(user), page, size, status));
    }

    @GetMapping("/{id}")
    public Result<OrderView> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(orderService.detailForMerchant(requireMerchant(user), id));
    }

    @PostMapping("/{id}/ship")
    public Result<OrderView> ship(@AuthenticationPrincipal CurrentUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody ShipRequest request) {
        return Result.ok(orderService.shipForMerchant(requireMerchant(user), id, request));
    }

    @PostMapping("/{id}/refund/approve")
    public Result<OrderView> approveRefund(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable Long id) {
        return Result.ok(orderService.approveRefundForMerchant(requireMerchant(user), id));
    }

    @PostMapping("/{id}/refund/reject")
    public Result<OrderView> rejectRefund(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable Long id) {
        return Result.ok(orderService.rejectRefundForMerchant(requireMerchant(user), id));
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
