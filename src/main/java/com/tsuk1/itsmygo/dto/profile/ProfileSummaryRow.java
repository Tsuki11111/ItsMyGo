package com.tsuk1.itsmygo.dto.profile;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户画像：已支付订单汇总行。 */
@Data
public class ProfileSummaryRow {
    private Long orderCount;
    private BigDecimal paidAmount;
    private LocalDateTime lastOrderAt;
}
