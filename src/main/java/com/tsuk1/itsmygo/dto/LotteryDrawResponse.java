package com.tsuk1.itsmygo.dto;

/**
 * 抽奖结果。
 *
 * <p>{@code result} 为 {@code WIN} / {@code THANKS}；谢谢参与时奖项与奖券码都是 null。
 * 结果会同时写入 MySQL，重复调用返回的是同一条记录。</p>
 */
public record LotteryDrawResponse(
        String result,
        Long prizeId,
        String prizeName,
        String prizeCode,
        String message
) {
}
