package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理端添加奖项。
 *
 * <p>{@code prizeType} 取 {@code COUPON} / {@code PHYSICAL}（不传默认 PHYSICAL）；
 * 券类奖品必须带 {@code couponId}，实物奖品可带 {@code image} 作为转盘图。</p>
 */
public record LotteryPrizeRequest(
        @NotBlank(message = "奖项名称不能为空")
        @Size(max = 100, message = "奖项名称最多 100 个字符")
        String name,

        @Size(max = 255, message = "奖品图 URL 最多 255 个字符")
        String image,

        /** COUPON / PHYSICAL；为空按 PHYSICAL 处理，兼容旧客户端 */
        @Size(max = 16, message = "奖品类型最多 16 个字符")
        String prizeType,

        /** 券类奖品关联的券模板 id */
        Long couponId,

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
