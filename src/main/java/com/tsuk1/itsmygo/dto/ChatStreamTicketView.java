package com.tsuk1.itsmygo.dto;

/** EventSource 不能带 Authorization，用短期一次性票据换 SSE 连接。 */
public record ChatStreamTicketView(String ticket, long expiresInSeconds) {
}
