package com.tsuk1.itsmygo.entity;

/** 实物奖品领取单状态 */
public enum LotteryClaimStatus {

    /** 中奖后等待用户填写收货地址 */
    PENDING_ADDRESS,

    /** 已填地址，等待管理端发货 */
    PENDING_SHIPMENT,

    /** 管理端已填运单号发货 */
    SHIPPED;

    public String label() {
        return switch (this) {
            case PENDING_ADDRESS -> "待填地址";
            case PENDING_SHIPMENT -> "待发货";
            case SHIPPED -> "已发货";
        };
    }
}
