package com.tsuk1.itsmygo.dto.report;

import lombok.Data;

import java.math.BigDecimal;

/** 品类 / 店铺 / 商品排行行（ReportMapper 用）。店铺聚合不填 count。 */
@Data
public class ConsumptionRankRow {

    private String name;

    private BigDecimal amount;

    private Long count;
}
