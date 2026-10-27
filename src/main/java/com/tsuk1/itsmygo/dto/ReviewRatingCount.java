package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 某个星级下的可见评价数，用于评分分布 */
@Data
public class ReviewRatingCount {
    private Integer rating;
    private Long count;
}
