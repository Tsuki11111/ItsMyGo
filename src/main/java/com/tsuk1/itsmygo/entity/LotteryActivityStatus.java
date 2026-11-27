package com.tsuk1.itsmygo.entity;

/** 抽奖活动状态：数据库存 code，业务代码用枚举 */
public enum LotteryActivityStatus {

    ACTIVE(1),
    ENDED(2);

    private final int code;

    LotteryActivityStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static LotteryActivityStatus fromCode(int code) {
        for (LotteryActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的抽奖活动状态: " + code);
    }
}
