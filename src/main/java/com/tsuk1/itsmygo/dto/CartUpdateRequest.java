package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartUpdateRequest(
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        Integer quantity,

        @NotNull(message = "勾选状态不能为空")
        Boolean selected
) {
}
