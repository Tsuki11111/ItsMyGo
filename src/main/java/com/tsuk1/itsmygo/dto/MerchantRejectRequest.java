package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台驳回商家申请 */
public record MerchantRejectRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 255, message = "驳回原因最长 255 个字符")
        String reason
) {
}
