package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(
        @NotNull(message = "商品 ID 不能为空")
        Long productId,

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        Integer quantity
) {
}
