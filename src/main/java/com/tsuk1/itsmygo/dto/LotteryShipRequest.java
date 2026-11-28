package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理端实物奖品发货：填快递单号 */
public record LotteryShipRequest(
        @NotBlank(message = "快递单号不能为空")
        @Size(max = 64, message = "快递单号最长 64 个字符")
        String trackingNo
) {
}
