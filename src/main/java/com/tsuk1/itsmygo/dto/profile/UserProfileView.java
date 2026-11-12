package com.tsuk1.itsmygo.dto.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户画像（规则统计版）：只返回聚合事实，不返回订单号、地址、聊天原文。
 *
 * <p>导购子智能体与智能推广共用同一份画像；冷启动用户返回 coldStart=true，
 * 由调用方回退到通用推荐/通用推广权重。</p>
 */
public record UserProfileView(
        boolean coldStart,
        long orderCount,
        BigDecimal paidAmount,
        BigDecimal avgOrderAmount,
        LocalDate lastOrderAt,
        List<CategoryPref> topCategories,
        List<BrandPref> topBrands,
        PriceBand priceBand,
        List<CategoryPref> recentViews,
        List<CategoryPref> favoriteCategories,
        Double avgRating) {

    public record CategoryPref(Long categoryId, String name, long count, BigDecimal amount) {
    }

    public record BrandPref(String name, long count) {
    }

    public record PriceBand(BigDecimal min, BigDecimal avg, BigDecimal max) {
    }
}
