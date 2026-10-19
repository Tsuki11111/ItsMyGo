package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotNull;

/** 客户端已看到的最大消息 id；服务端只允许已读指针前进。 */
public record ChatReadRequest(@NotNull Long lastMessageId) {
}
