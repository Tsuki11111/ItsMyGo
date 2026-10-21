package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 店铺信息：公开店铺页、商家自己的资料、申请状态都用它；公开店铺页的 ship* 会脱敏 */
public record MerchantView(
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
        LocalDateTime createdAt
) {
}
