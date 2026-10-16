package com.tsuk1.itsmygo.entity;

/**
 * 订单状态机。**合法流转只认这一个地方**，不要散落到各个 Service 里判断。
 *
 * <pre>
 * 待支付 ──支付──> 已支付 ──发货──> 已发货 ──确认收货──> 已完成
 *    │                 │              │                │
 *    │                 └──────────────┴────────────────┘
 *    │                              ↓ 申请退款
 *    │                         退款中 ──同意──> 已退款（终态）
 *    │                           │
 *    └──取消──> 已取消（终态）      └──驳回──> 回到申请前的状态
 * </pre>
 *
 * 已发货之后不能再取消（货已经出去了），只能走退款。
 * 已退款 / 已取消 / 已完成 是终态（已完成仍可申请退款 —— 相当于七天无理由）。
 */
public enum OrderStatus {

    PENDING_PAYMENT("待支付"),
    PAID("已支付"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REFUNDING("退款中"),
    REFUNDED("已退款");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED || target == REFUNDING;
            case SHIPPED -> target == COMPLETED || target == REFUNDING;
            case COMPLETED -> target == REFUNDING;
            // 退款被驳回不是「流转到某个固定状态」，而是回到申请前那个状态，
            // 所以这里放行到 PAID / SHIPPED / COMPLETED，具体由 Service 校验一致性
            case REFUNDING -> target == REFUNDED || target == PAID
                    || target == SHIPPED || target == COMPLETED;
            case REFUNDED, CANCELLED -> false;
        };
    }

    /** 取消要回滚库存；只有「还没发货」的两个状态可以取消 */
    public boolean cancellable() {
        return this == PENDING_PAYMENT || this == PAID;
    }

    /** 可以申请退款的状态：付过钱、且还没退过 */
    public boolean refundable() {
        return this == PAID || this == SHIPPED || this == COMPLETED;
    }
}
