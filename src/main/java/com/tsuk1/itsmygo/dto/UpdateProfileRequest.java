package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
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

        @Size(max = 20, message = "手机号最长 20 个字符")
        String phone
) {
}
