package com.tsuk1.itsmygo.dto;

public record LoginResponse(String token, Long userId, String username, String nickname,
                            String avatar, String role,
                            /** 商家账号有值；其他角色为 null */
                            Long merchantId,
                            String merchantName,
                            String merchantStatus) {
}
