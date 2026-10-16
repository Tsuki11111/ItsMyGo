package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.ProductSpec;

import java.util.List;

/** 商品的规格与 SKU —— 管理端回显、顾客端详情组装都用它 */
public record ProductSkuView(List<ProductSpec> specs, List<ProductSku> skus) {
}
