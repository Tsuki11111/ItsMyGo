package com.tsuk1.itsmygo.dto.agent;

/** agent 准备催发货的入参：只认订单号，订单归属由 Java 按票据用户校验。 */
public record AgentUrgeShipmentRequest(String orderNo) {
}
