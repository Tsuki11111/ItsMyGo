package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 低库存 SKU 行（AdminDashboardMapper 用）。 */
@Data
public class AdminLowStockSku {

    private Long productId;

    private String productName;

    private String image;

    private Long skuId;

    private String specText;

    private Integer stock;
}
