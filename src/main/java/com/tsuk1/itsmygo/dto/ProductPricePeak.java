package com.tsuk1.itsmygo.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 某个商品在时间窗口内的最高价（智能推广「降价」信号的输入，批量查询避免 N+1）。 */
@Data
public class ProductPricePeak {

    private Long productId;

    private BigDecimal maxPrice;
}
