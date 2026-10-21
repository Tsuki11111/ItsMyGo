package com.tsuk1.itsmygo.dto.agent;

/**
 * agent 的工具入参。**没有 userId** —— 用户身份只从票据解析，
 * 模型即使被注入也传不了别人的身份。
 */
public record AgentOrderQueryRequest(String orderNo, String status, Integer limit) {
}
