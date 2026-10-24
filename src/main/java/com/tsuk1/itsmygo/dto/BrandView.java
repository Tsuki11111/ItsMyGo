package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 品牌专区：一个品牌名 + 在售商品数 */
@Data
public class BrandView {
    private String name;
    private Long count;
}
