package com.tsuk1.itsmygo.dto.report;

import lombok.Data;

import java.math.BigDecimal;

/** 按月消费聚合行（ReportMapper 用），month 形如 2026-11。 */
@Data
public class ConsumptionMonthlyRow {

    private String month;

    private BigDecimal amount;
}
