package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.Size;

/**
 * agent 准备退款申请的入参：订单号 + 会话内表单收集的退款原因。
 * 订单归属由 Java 按票据用户校验；原因只落 draft 快照，确认时才传给 RefundRequest。
 */
public record AgentRefundDraftRequest(
        String orderNo,
        @Size(max = 200, message = "退款原因最长 200 个字符")
        String reason) {
}
