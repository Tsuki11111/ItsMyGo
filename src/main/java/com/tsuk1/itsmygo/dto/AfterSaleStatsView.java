package com.tsuk1.itsmygo.dto;

import java.util.Map;

/** 售后计数（商家看板 / 管理端概览）：按状态聚合。 */
public record AfterSaleStatsView(Map<String, Long> counts, long total) {
}
