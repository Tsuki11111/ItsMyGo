package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端新建/编辑优惠券模板 */
public record CouponRequest(
        @NotBlank(message = "券名称不能为空")
        @Size(max = 64, message = "券名称最长 64 个字符")
        String name,

        @NotNull(message = "使用门槛不能为空")
        @DecimalMin(value = "0.00", message = "使用门槛不能为负数")
        BigDecimal thresholdAmount,

        @NotNull(message = "减免金额不能为空")
        @DecimalMin(value = "0.01", message = "减免金额必须大于 0")
        BigDecimal discountAmount,

        @NotNull(message = "发放总量不能为空")
        @Min(value = 1, message = "发放总量至少为 1")
        Integer totalCount,

        @NotNull(message = "有效期开始时间不能为空")
        LocalDateTime validFrom,

        @NotNull(message = "有效期结束时间不能为空")
        LocalDateTime validTo,

        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
