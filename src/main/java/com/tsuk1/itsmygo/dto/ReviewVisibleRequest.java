package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotNull;

/** 管理端显示/隐藏评价 */
public record ReviewVisibleRequest(
        @NotNull(message = "显示状态不能为空")
        Boolean visible
) {
}
