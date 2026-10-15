package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;

/**
 * 购物车项视图：购物车行本身 + 商品**当前**信息（价格/库存/上架状态都可能变）。
 * 下单时才把它们固化成订单快照。
 */
public record CartItemView(
        Long id,
        Long productId,
        String productName,
        String productImage,
        BigDecimal price,
        Integer stock,
        Integer quantity,
        BigDecimal subtotal,
        boolean selected,
        boolean onSale,
        /** 在售且库存够本次数量 */
        boolean available
) {
}
