package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户中心收藏列表项：收藏记录 + 商品**当前**信息 */
public record FavoriteView(
        Long id,
        Long productId,
        String name,
        String subtitle,
        String mainImage,
        BigDecimal price,
        /** 1 上架 / 0 下架；商品已删除时也为 0 */
        Integer status,
        LocalDateTime createdAt,
        Long merchantId,
        String merchantName
) {
}
