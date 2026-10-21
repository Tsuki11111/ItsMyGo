package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 平台后台的商户列表项：多带一个店主用户名 */
public record AdminMerchantView(
        Long id,
        String name,
        String logo,
        String description,
        String contactName,
        String contactPhone,
        String shipProvince,
        String shipCity,
        String shipDistrict,
        String shipAddress,
        BigDecimal shipLng,
        BigDecimal shipLat,
        String status,
        String rejectReason,
        Long ownerUserId,
        String ownerUsername,
        LocalDateTime createdAt
) {
}
