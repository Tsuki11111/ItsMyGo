package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminInvitationCreateRequest(
        @NotBlank(message = "请输入要邀请的用户名")
        @Size(max = 32, message = "用户名最长 32 个字符")
        String username) {
}
