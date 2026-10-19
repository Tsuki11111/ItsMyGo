package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;

/**
 * 购物车项视图：购物车行本身 + **商品与 SKU 的当前**信息
 * （价格 / 库存 / 上架状态都可能变）。下单时才把它们固化成订单快照。
 */
public record CartItemView(
        Long id,
        Long productId,
        /** 具体买哪个规格，下单时带着它走 */
        Long skuId,
        String productName,
        String productImage,
        /** 规格描述，如「颜色:黑色;版本:静音版」；无规格商品是空串 */
        String specText,
        BigDecimal price,
        Integer stock,
        Integer quantity,
        BigDecimal subtotal,
        boolean selected,
        boolean onSale,
        /** 商品和规格都在售、店铺营业中，且库存够本次数量 */
        boolean available,
        /** 所属商户；商品已删除时为 null */
        Long merchantId,
        String merchantName,
        /** 店铺是否 ACTIVE */
        boolean merchantActive
) {
}
