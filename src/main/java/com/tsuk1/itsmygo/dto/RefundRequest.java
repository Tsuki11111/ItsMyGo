package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Size;

/** 用户申请退款时填写的退款原因；允许为空（智能体代申请/老客户端）。 */
public record RefundRequest(
        @Size(max = 200, message = "退款原因最长 200 个字符")
        String reason) {
}
