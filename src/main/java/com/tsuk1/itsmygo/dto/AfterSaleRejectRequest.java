package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商家 / 平台驳回售后：必须写理由。 */
public record AfterSaleRejectRequest(
        @NotBlank(message = "请填写驳回理由")
        @Size(max = 255, message = "驳回理由最长 255 个字符")
        String reason) {
}
