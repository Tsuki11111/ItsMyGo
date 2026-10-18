package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料。
 *
 * 头像**故意不放在这里** —— 它只能通过上传接口拿到服务端生成的 URL，
 * 放进 DTO 就等于允许用户提交任意地址。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 32, message = "昵称最长 32 个字符")
        String nickname,

        /**
         * 选填。允许空串（表示不填），填了就必须是 11 位手机号。
         * null 会被 Bean Validation 跳过，空串靠正则里的 {@code ^$} 分支放行。
         */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
        String phone
) {
}
