package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理端添加奖项。
 *
 * <p>{@code image} 是转盘上的实物图 URL，可为空；{@code weight} 越大越容易中，
 * 小额券这类小奖配更高权重。</p>
 */
public record LotteryPrizeRequest(
        @NotBlank(message = "奖项名称不能为空")
        @Size(max = 100, message = "奖项名称最多 100 个字符")
        String name,

        @Size(max = 255, message = "奖品图 URL 最多 255 个字符")
        String image,

        @NotNull(message = "奖项等级不能为空")
        @Min(value = 1, message = "奖项等级至少为 1")
        Integer level,

        @NotNull(message = "权重不能为空")
        @Min(value = 1, message = "权重至少为 1")
        Integer weight,

        @NotNull(message = "库存不能为空")
        @Min(value = 1, message = "库存至少为 1")
        Integer stock
) {
}
