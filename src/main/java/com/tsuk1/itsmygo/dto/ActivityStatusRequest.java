package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 管理端修改活动状态：1 进行中 / 2 已结束 */
public record ActivityStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 1, message = "状态只能是 1 或 2")
        @Max(value = 2, message = "状态只能是 1 或 2")
        Integer status
) {
}
