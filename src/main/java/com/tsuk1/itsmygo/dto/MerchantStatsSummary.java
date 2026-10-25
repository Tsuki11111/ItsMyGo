package com.tsuk1.itsmygo.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 商户看板的单行聚合结果（MerchantStatsMapper 用）。 */
@Data
public class MerchantStatsSummary {

    private BigDecimal gmv30;

    private Long paidOrders30;

    private Long orders30;

    private Long refunding30;

    private BigDecimal todayGmv;

    private Long todayOrders;

    private Long pendingShipment;

    private Long pendingRefund;
}
