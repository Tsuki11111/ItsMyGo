package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.entity.Order;

import java.util.List;

/**
 * 下单补账（M31 / D1）：把「占位订单」补成真正可支付的订单，或把失败订单收尾。
 */
public interface OrderSettlementService {

    /**
     * 幂等补账：DB 条件扣库存 → 占用券 → 清购物车快照 → CAS 转 PENDING_PAYMENT
     * → 事务提交后发超时关单消息。返回本次真正补账成功的订单（已被别人处理过的会被跳过）。
     */
    List<Order> settle(List<Long> orderIds, List<Long> cartItemIds);

    /**
     * 失败收尾：CAS CREATING → CREATE_FAILED，回补 Redis 预扣、回滚券、写失败原因。
     * 并发下只有抢到 CAS 的那次会做补偿（幂等）。
     */
    void failCreation(List<Long> orderIds, String reason);
}
