package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理端添加奖项：多奖项等概率，所以不需要 weight */
public record LotteryPrizeRequest(
        @NotBlank(message = "奖项名称不能为空")
        @Size(max = 100, message = "奖项名称最多 100 个字符")
        String name,

        @NotNull(message = "奖项等级不能为空")
        @Min(value = 1, message = "奖项等级至少为 1")
        Integer level,

        @NotNull(message = "库存不能为空")
        @Min(value = 1, message = "库存至少为 1")
        Integer stock
) {
}
