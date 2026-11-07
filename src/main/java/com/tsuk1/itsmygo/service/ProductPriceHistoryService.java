package com.tsuk1.itsmygo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/** 商品价格历史：只在价格真的变化时追加，给智能推广算「最近降价」。 */
public interface ProductPriceHistoryService {

    /** 商品新建 / 迁移时的初始价格 */
    void recordBaseline(Long productId, Long skuId, BigDecimal price);

    /** 价格真的变了才记一条 update；skuId 为空表示商品级改价 */
    void recordIfChanged(Long productId, Long skuId, BigDecimal oldPrice, BigDecimal newPrice);

    /** 批量取窗口内最高价，没有历史的商品不在结果里 */
    Map<Long, BigDecimal> maxPricesSince(Collection<Long> productIds, LocalDateTime since);
}
