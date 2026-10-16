package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;

import java.util.List;

/**
 * 订单视图：订单头 + 明细 + 状态中文名。
 * 明细是**下单时的快照**，不查商品表。
 *
 * @param items     明细。列表场景会按 previewLimit 截断（卡片上只显示前几件）
 * @param itemCount 该订单的**总件数**（不受截断影响）—— 前端显示「共 N 件」用
 */
public record OrderView(Order order, String statusLabel, List<OrderItem> items, int itemCount) {

    /** 详情场景：明细不截断，itemCount 就是 items 的长度 */
    public static OrderView full(Order order, String statusLabel, List<OrderItem> items) {
        return new OrderView(order, statusLabel, items, items.size());
    }
}
