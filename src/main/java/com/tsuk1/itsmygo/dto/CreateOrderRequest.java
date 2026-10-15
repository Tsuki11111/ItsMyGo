package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotNull;

/** 下单：只传收货地址，商品从购物车勾选项取 */
public record CreateOrderRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId
) {
}
