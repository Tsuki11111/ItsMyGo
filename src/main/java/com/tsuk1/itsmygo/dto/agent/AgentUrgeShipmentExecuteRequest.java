package com.tsuk1.itsmygo.dto.agent;

/** agent 完成催发货确认后，调用 Java 发送工具时的入参。 */
public record AgentUrgeShipmentExecuteRequest(String actionNo) {
}
