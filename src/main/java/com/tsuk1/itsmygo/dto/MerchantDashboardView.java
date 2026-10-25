package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 商家后台经营看板：今日 / 近 30 天 / 待办 / 趋势 / 热销 / 低库存 / 最近订单。 */
public record MerchantDashboardView(
        String merchantName,
        String todayGmv,
        long todayOrders,
        String gmv30,
        long paidOrders30,
        long orders30,
        long refunding30,
        String avgOrderValue30,
        long pendingShipment,
        long pendingRefund,
        long onSaleProducts,
        long lowStockSkus,
        List<AdminDashboardView.TrendPoint> trend,
        List<TopProduct> topProducts,
        List<LowStock> lowStockList,
        List<RecentOrder> recentOrders) {

    public record TopProduct(Long productId, String name, String image, String price,
                             int stock, int sales) {
    }

    public record LowStock(Long productId, String productName, String image, Long skuId,
                           String specText, int stock) {
    }

    public record RecentOrder(Long id, String orderNo, String totalAmount, String status,
                              String statusLabel, String createdAt) {
    }
}
