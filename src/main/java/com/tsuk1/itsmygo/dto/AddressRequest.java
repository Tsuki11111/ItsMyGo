package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 32, message = "收货人最长 32 个字符")
        String receiver,

        @NotBlank(message = "联系电话不能为空")
        @Size(max = 20, message = "联系电话最长 20 个字符")
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
