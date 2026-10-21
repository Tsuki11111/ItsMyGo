package com.tsuk1.itsmygo.dto.agent;

import java.util.List;

/** 给 agent 的商品搜索结果：带上 SKU，模型才能把用户的话落到具体规格。 */
public record AgentProductView(
        Long productId,
        String name,
        String subtitle,
        String mainImage,
        String price,
        boolean hasSpecs,
        List<Sku> skus) {

    public record Sku(Long skuId, String specText, String price, Integer stock) {
    }
}
