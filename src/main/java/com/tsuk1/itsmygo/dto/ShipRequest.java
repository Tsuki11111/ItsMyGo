package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端发货 */
public record ShipRequest(
        @NotBlank(message = "物流单号不能为空")
        @Size(max = 64, message = "物流单号最长 64 个字符")
        String trackingNo
) {
}
