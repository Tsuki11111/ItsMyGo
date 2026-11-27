package com.tsuk1.itsmygo.dto;

/** 顾客端奖池：只展示奖项、实物图与实时剩余库存，不暴露内部权重 */
public record LotteryPrizePublicView(
        Long id,
        String name,
        String image,
        Integer level,
        long remainingStock
) {
}
