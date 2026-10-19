package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 领券中心列表项 */
public record CouponView(
        Long id,
        /** NULL=平台券 */
        Long merchantId,
        String merchantName,
        String name,
        BigDecimal thresholdAmount,
        BigDecimal discountAmount,
        Integer totalCount,
        Integer claimedCount,
        Integer remainingCount,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        /** 当前用户是否已领取；匿名时恒为 false */
        boolean claimed
) {
}
