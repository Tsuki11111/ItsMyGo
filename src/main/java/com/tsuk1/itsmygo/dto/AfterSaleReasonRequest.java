package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理端维护售后原因字典。 */
public record AfterSaleReasonRequest(
        @NotBlank(message = "请填写原因编码")
        @Pattern(regexp = "[A-Z0-9_]{2,32}", message = "编码只能是 2–32 位大写字母 / 数字 / 下划线")
        String code,

        @NotBlank(message = "请填写原因名称")
        @Size(max = 64, message = "名称最长 64 个字符")
        String label,

        Integer sort,

        /** 1 启用 / 0 停用；不传默认启用 */
        Integer status) {
}
