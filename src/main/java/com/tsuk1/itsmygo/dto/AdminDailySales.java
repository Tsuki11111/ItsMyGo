package com.tsuk1.itsmygo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 日成交聚合行（AdminDashboardMapper 用）。 */
@Data
public class AdminDailySales {

    private LocalDate day;

    private BigDecimal amount;

    private Long orderCount;
}
