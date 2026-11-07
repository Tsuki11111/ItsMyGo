package com.tsuk1.itsmygo.dto.report;

import lombok.Data;

import java.math.BigDecimal;

/** 期间汇总的单行聚合结果（ReportMapper 用）。 */
@Data
public class ConsumptionSummaryRow {

    private Long orderCount;

    private BigDecimal paidAmount;

    private BigDecimal refundedAmount;

    private BigDecimal discountAmount;
}
