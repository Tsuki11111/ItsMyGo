package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 售后单里的一个订单项：退这一项、退多少件。 */
public record AfterSaleItemRequest(
        @NotNull(message = "请选择要退的商品")
        Long orderItemId,
        @NotNull(message = "请填写退款数量")
        @Min(value = 1, message = "退款数量至少 1 件")
        Integer quantity) {
}
