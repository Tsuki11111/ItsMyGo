package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 商家入驻申请 / 店铺资料修改 */
public record MerchantRequest(
        @NotBlank(message = "店铺名不能为空")
        @Size(max = 64, message = "店铺名最长 64 个字符")
        String name,

        @Size(max = 512, message = "Logo 地址最长 512 个字符")
        String logo,

        @Size(max = 500, message = "店铺简介最长 500 个字符")
        String description,

        @NotBlank(message = "联系人不能为空")
        @Size(max = 32, message = "联系人最长 32 个字符")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
        String contactPhone
) {
}
