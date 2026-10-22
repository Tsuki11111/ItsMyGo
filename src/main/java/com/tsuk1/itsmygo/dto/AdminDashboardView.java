package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 管理后台数据概览：核心指标 + 待办 + 近 7 天成交趋势。 */
public record AdminDashboardView(
        String todayGmv,
        long todayOrders,
        long todayPaidOrders,
        long pendingShipments,
        long pendingRefunds,
        long pendingMerchants,
        long lowStockSkus,
        long totalUsers,
        long activeMerchants,
        long onSaleProducts,
        List<TrendPoint> trend,
        List<PendingOrder> pendingShipmentOrders,
        List<PendingOrder> pendingRefundOrders,
        List<PendingMerchant> pendingMerchantList,
        List<LowStock> lowStockList) {

    public record TrendPoint(String date, String amount, long orders) {
    }

    public record PendingOrder(Long id, String orderNo, String merchantName,
                               String totalAmount, String statusLabel, String createdAt) {
    }

    public record PendingMerchant(Long id, String name, String contactName, String createdAt) {
    }

    public record LowStock(Long productId, String productName, String image, Long skuId,
                           String specText, Integer stock) {
    }
}
