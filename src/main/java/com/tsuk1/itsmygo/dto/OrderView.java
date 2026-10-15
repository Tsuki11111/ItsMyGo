package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;

import java.util.List;

/** 订单详情：订单头 + 明细（明细是下单时的快照，不查商品表） */
public record OrderView(Order order, String statusLabel, List<OrderItem> items) {
}
