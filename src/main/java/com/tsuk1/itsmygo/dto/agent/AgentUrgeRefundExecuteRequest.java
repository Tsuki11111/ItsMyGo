package com.tsuk1.itsmygo.dto.agent;

/** agent 完成催退款确认后，调用 Java 发送工具时的入参。 */
public record AgentUrgeRefundExecuteRequest(String actionNo) {
}
