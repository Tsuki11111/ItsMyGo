package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;

import java.util.List;

/**
 * 一次结算的结果：按商户拆成 **N 个订单**，每个订单独立支付/发货/退款。
 *
 * 顶层字段（order / statusLabel / items / itemCount）**镜像第一个子订单**，
 * 保持与单商户时代的响应结构兼容；新前端使用 {@code orders} 拿到全部子订单。
 */
public record CheckoutView(
        List<OrderView> orders,
        Order order,
        String statusLabel,
        List<OrderItem> items,
        int itemCount
) {

    public static CheckoutView of(List<OrderView> orders) {
        if (orders.isEmpty()) {
            return new CheckoutView(List.of(), null, "", List.of(), 0);
        }
        OrderView first = orders.get(0);
        return new CheckoutView(orders, first.order(), first.statusLabel(),
                first.items(), first.itemCount());
    }
}
