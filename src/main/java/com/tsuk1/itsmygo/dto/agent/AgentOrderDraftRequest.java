package com.tsuk1.itsmygo.dto.agent;

import java.util.List;

/**
 * agent 准备下单的入参。**没有 userId** —— 身份从票据解析；
 * 金额、库存、优惠都由 Java 在 prepare / confirm 时重新计算。
 */
public record AgentOrderDraftRequest(
        Long addressId,
        List<Item> items) {

    public record Item(Long productId, Long skuId, Integer quantity) {
    }
}
