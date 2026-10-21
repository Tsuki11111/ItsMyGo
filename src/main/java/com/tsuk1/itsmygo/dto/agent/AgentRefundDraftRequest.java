package com.tsuk1.itsmygo.dto.agent;

/** agent 准备退款申请的入参：只认订单号，订单归属由 Java 按票据用户校验。 */
public record AgentRefundDraftRequest(String orderNo) {
}
