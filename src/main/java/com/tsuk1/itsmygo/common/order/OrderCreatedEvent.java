package com.tsuk1.itsmygo.common.order;

import java.util.List;

/**
 * 订单已创建事件（异步补账用）。
 *
 * <p>消费端**不信任事件内容**，以 DB 里的 CREATING 订单/明细为准做幂等；
 * 事件只负责“告诉消费端有哪些订单要补账、要清哪些购物车行”。</p>
 */
public record OrderCreatedEvent(List<Long> orderIds, List<Long> cartItemIds) {
}
