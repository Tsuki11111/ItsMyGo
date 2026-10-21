package com.tsuk1.itsmygo.dto.agent;

/** 给 agent 的收货地址视图（当前用户自己的数据）。 */
public record AgentAddressView(
        Long id,
        String receiver,
        String phone,
        String address,
        boolean isDefault) {
}
