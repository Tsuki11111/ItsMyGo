package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 下单：只传收货地址 + 提交令牌，商品从购物车勾选项取 */
public record CreateOrderRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId,

        /**
         * 提交令牌，先调 GET /api/orders/token 领取。
         * 一次性消费，重复提交会因令牌已被消费而被拒。
         */
        @NotBlank(message = "提交令牌不能为空，请刷新后重试")
        String token
) {
}
