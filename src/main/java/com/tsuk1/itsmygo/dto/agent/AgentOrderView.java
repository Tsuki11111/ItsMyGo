package com.tsuk1.itsmygo.dto.agent;

import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 给 agent 看的订单快照。**刻意不含收货人 / 电话 / 地址** ——
 * agent 只需要回答状态、商品与物流，最小化暴露 PII。
 */
public record AgentOrderView(
        String orderNo,
        String status,
        String statusLabel,
        String merchantName,
        String totalAmount,
        String trackingNo,
        String createdAt,
        String paidAt,
        String shippedAt,
        List<Item> items) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Item(String productName, String specText, Integer quantity, String unitPrice) {
    }

    public static AgentOrderView from(OrderView view) {
        Order order = view.order();
        List<Item> items = (view.items() == null ? List.<OrderItem>of() : view.items()).stream()
                .map(item -> new Item(
                        nullToEmpty(item.getProductName()),
                        nullToEmpty(item.getSpecText()),
                        item.getQuantity(),
                        item.getUnitPrice() == null ? "" : item.getUnitPrice().toPlainString()))
                .toList();
        return new AgentOrderView(
                nullToEmpty(order.getOrderNo()),
                nullToEmpty(order.getStatus()),
                nullToEmpty(view.statusLabel()),
                nullToEmpty(order.getMerchantName()),
                order.getTotalAmount() == null ? "" : order.getTotalAmount().toPlainString(),
                nullToEmpty(order.getTrackingNo()),
                format(order.getCreatedAt()),
                format(order.getPaidAt()),
                format(order.getShippedAt()),
                items);
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
