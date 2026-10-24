package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 商户商品运营聚合行。 */
@Data
public class AdminMerchantInventoryRow {

    private Long merchantId;

    private Long onSaleProducts;

    private Long lowStockSkus;
}
