package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 平台后台的商户列表项：多带一个店主用户名 */
public record AdminMerchantView(
        Long id,
        String name,
        String logo,
        String description,
        String contactName,
        String contactPhone,
        String status,
        String rejectReason,
        Long ownerUserId,
        String ownerUsername,
        LocalDateTime createdAt
) {
}
