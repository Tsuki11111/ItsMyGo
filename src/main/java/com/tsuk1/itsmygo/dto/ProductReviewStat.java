package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 批量评价统计（商品列表回填评分 / 评价数，避免 N+1）。 */
@Data
public class ProductReviewStat {

    private Long productId;

    private Long reviewCount;

    private Double averageRating;
}
