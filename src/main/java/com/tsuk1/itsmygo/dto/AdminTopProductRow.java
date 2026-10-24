package com.tsuk1.itsmygo.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 商户热销商品行。 */
@Data
public class AdminTopProductRow {

    private Long productId;

    private String name;

    private String image;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;
}
