package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotNull;

/** 发货前修改订单收货地址：只传已保存的地址 id，快照由服务端复制到订单。 */
public record OrderAddressUpdateRequest(
        @NotNull(message = "请选择收货地址") Long addressId) {
}
