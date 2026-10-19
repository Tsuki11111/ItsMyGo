package com.tsuk1.itsmygo.dto;

import java.time.LocalDateTime;

/** 店铺信息：公开店铺页、商家自己的资料、申请状态都用它 */
public record MerchantView(
        Long id,
        String name,
        String logo,
        String description,
        String contactName,
        String contactPhone,
        String status,
        String rejectReason,
        Long ownerUserId,
        LocalDateTime createdAt
) {
}
