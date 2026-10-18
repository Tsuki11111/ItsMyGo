package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 32, message = "收货人最长 32 个字符")
        String receiver,

        /**
         * 中国大陆手机号：1 开头、第二位 3-9、共 11 位。
         * 和淘宝/京东的口径一致 —— 只收手机号，不接座机。
         */
        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
        String phone,

        @NotBlank(message = "省份不能为空")
        @Size(max = 32, message = "省份最长 32 个字符")
        String province,

        @NotBlank(message = "城市不能为空")
        @Size(max = 32, message = "城市最长 32 个字符")
        String city,

        @NotBlank(message = "区/县不能为空")
        @Size(max = 32, message = "区/县最长 32 个字符")
        String district,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 128, message = "详细地址最长 128 个字符")
        String detail,

        @NotNull(message = "是否设为默认地址不能为空")
        Boolean isDefault
) {
}
