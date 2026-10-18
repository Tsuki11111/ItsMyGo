package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 提交评价：一个订单项只能评一次 */
public record ReviewRequest(
        @NotNull(message = "订单项不能为空")
        Long orderItemId,

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        Integer rating,

        @Size(max = 500, message = "评价内容最长 500 个字符")
        String content
) {
}
