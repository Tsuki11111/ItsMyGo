package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 实时库存：数据来自 Redis 的计数器，不走商品详情缓存 */
public record StockView(Long productId, int stock, boolean available, List<SkuStockView> skus) {

    /** 单个 SKU 的实时余量 —— 前端选规格时要立刻看到那一款还剩多少 */
    public record SkuStockView(Long skuId, int stock, boolean available) {
    }
}
