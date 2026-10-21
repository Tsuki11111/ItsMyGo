package com.tsuk1.itsmygo.dto.agent;

import java.util.List;

/**
 * 给 agent / 导购用的商品视图。
 * 除了下单所需的 SKU，还带销量、评分、库存、店铺、分类，供推荐排序与解释。
 */
public record AgentProductView(
        Long productId,
        String name,
        String subtitle,
        /** 商品详情纯文本摘要（截断），仅用于语义索引，不必原样转述 */
        String description,
        String mainImage,
        String price,
        Integer totalStock,
        Integer sales,
        String rating,
        Long reviewCount,
        String merchantName,
        String categoryName,
        boolean hasSpecs,
        List<Sku> skus) {

    public record Sku(Long skuId, String specText, String price, Integer stock) {
    }
}
