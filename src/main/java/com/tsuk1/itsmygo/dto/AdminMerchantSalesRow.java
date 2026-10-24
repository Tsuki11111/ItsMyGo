package com.tsuk1.itsmygo.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 商户近 30 天成交聚合行。 */
@Data
public class AdminMerchantSalesRow {

    private Long merchantId;

    private BigDecimal gmv30;

    private Long orders30;

    private Long refunding30;
}
