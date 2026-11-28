package com.tsuk1.itsmygo.dto;

/**
 * 抽奖结果。
 *
 * <p>{@code result} 为 {@code WIN} / {@code THANKS}；谢谢参与时奖项与奖券码都是 null。
 * 券类奖品中奖后已自动发到「我的优惠券」，实物奖品在「我的奖品」里填收货地址。</p>
 */
public record LotteryDrawResponse(
        String result,
        Long prizeId,
        String prizeName,
        /** COUPON / PHYSICAL；谢谢参与时为 null */
        String prizeType,
        String prizeCode,
        String message
) {
}
