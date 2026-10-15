package com.tsuk1.itsmygo.dto;

/** 实时库存：数据来自 Redis 的库存计数器，不走商品详情缓存 */
public record StockView(Long productId, int stock, boolean available) {
}
