package com.tsuk1.itsmygo.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * agent 会话内 address_edit 表单回传的地址内容。
 *
 * <p>字段与 {@code AddressRequest} 对齐（同一套校验），故意**不带 isDefault**：
 * 内部工具只改收货信息，不能顺带清掉用户的默认地址标记。
 */
public record AgentAddressEditRequest(
        @NotNull(message = "地址 id 不能为空")
        Long addressId,

        @NotBlank(message = "收货人不能为空")
        @Size(max = 32, message = "收货人最长 32 个字符")
        String receiver,

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
        String detail) {
}
