package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminInvitationAcceptRequest(
        @NotBlank(message = "邀请令牌不能为空")
        String token) {
}
