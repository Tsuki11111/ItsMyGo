package com.tsuk1.itsmygo.dto.profile;

import lombok.Data;

import java.math.BigDecimal;

/** 用户画像：已购买商品的价格带。 */
@Data
public class ProfilePriceRow {
    private BigDecimal minPrice;
    private BigDecimal avgPrice;
    private BigDecimal maxPrice;
}
