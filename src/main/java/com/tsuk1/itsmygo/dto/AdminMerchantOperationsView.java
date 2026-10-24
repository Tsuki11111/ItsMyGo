package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 商户运营详情：KPI + 近 7 天趋势 + 热销商品 + 最近订单。 */
public record AdminMerchantOperationsView(
        AdminMerchantView merchant,
        String gmv30,
        long orders30,
        long refunding30,
        long onSaleProducts,
        long lowStockSkus,
        List<AdminDashboardView.TrendPoint> trend,
        List<TopProduct> topProducts,
        List<RecentOrder> recentOrders) {

    public record TopProduct(Long productId, String name, String image, String price,
                             int stock, int sales) {
    }

    public record RecentOrder(Long id, String orderNo, String totalAmount, String status,
                              String statusLabel, String createdAt) {
    }
}
