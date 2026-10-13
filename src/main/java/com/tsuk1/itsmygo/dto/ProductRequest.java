package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull(message = "所属分类不能为空")
        Long categoryId,

        @NotBlank(message = "商品名不能为空")
        @Size(max = 128, message = "商品名最长 128 个字符")
        String name,

        @Size(max = 255, message = "副标题最长 255 个字符")
        String subtitle,

        @Size(max = 512, message = "主图地址最长 512 个字符")
        String mainImage,

        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.00", message = "价格不能为负数")
        BigDecimal price,

        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负数")
        Integer stock,

        String detail,

        @NotNull(message = "上架状态不能为空")
        Boolean onSale
) {
}
