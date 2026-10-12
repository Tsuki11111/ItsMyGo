package com.tsuk1.itsmygo.dto;

public record LoginResponse(String token, Long userId, String username, String nickname) {
}
