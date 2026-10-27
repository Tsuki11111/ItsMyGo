package com.tsuk1.itsmygo.dto;

import java.util.Map;

/** 商品评价汇总；distribution 固定包含 5→1 星，未出现的星级补 0 */
public record ReviewSummary(double averageRating, long count, Map<Integer, Long> distribution) {
}
