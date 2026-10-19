package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户券视图：status 是展示状态（可能是 SQL 计算出来的 EXPIRED） */
public record UserCouponView(
        Long id,
        Long couponId,
        /** NULL=平台券 */
        Long merchantId,
        String merchantName,
        String couponName,
        BigDecimal thresholdAmount,
        BigDecimal discountAmount,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String status,
        Long usedOrderId,
        LocalDateTime createdAt
) {
}
