package com.tsuk1.itsmygo.dto.profile;

import lombok.Data;

import java.math.BigDecimal;

/** 用户画像：品类排行行。 */
@Data
public class ProfileCategoryRow {
    private Long categoryId;
    private String name;
    private Long count;
    private BigDecimal amount;
}
