package com.tsuk1.itsmygo.entity;

/**
 * 订单状态机。**合法流转只认这一个地方**，不要散落到各个 Service 里判断。
 *
 * <pre>
 * 待支付 ──支付──> 已支付 ──发货──> 已发货 ──确认收货──> 已完成
 *    │                 │
 *    └──取消──> 已取消  └──取消──> 已取消
 * </pre>
 *
 * 已发货之后不能再取消（货已经出去了），已完成/已取消是终态。
 */
public enum OrderStatus {

    PENDING_PAYMENT("待支付"),
    PAID("已支付"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

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
            case PAID -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** 取消要回滚库存；只有「还没发货」的两个状态可以取消 */
    public boolean cancellable() {
        return this == PENDING_PAYMENT || this == PAID;
    }
}
