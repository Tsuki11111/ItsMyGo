package com.tsuk1.itsmygo.dto;

/** 顾客端奖池：只展示奖项和实时剩余库存，不暴露内部库存字段 */
public record LotteryPrizePublicView(
        Long id,
        String name,
        Integer level,
        long remainingStock
) {
}
