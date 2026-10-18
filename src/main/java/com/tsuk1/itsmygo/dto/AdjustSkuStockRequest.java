package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理端调整某个 SKU 的库存。
 *
 * {@code expectedStock} 是提交时表单看到的库存，用来做**条件更新**（CAS）：
 * 只有数据库里的值还是它时才写入，否则说明期间有下单/取消改过库存，
 * 直接覆盖会把并发扣减冲掉（丢更新）。
 */
public record AdjustSkuStockRequest(
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负数")
        Integer stock,

        @NotNull(message = "原库存不能为空")
        @Min(value = 0, message = "原库存不能为负数")
        Integer expectedStock
) {
}
