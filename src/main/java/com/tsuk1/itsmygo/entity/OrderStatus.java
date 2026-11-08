package com.tsuk1.itsmygo.entity;

/**
 * 订单状态机。**合法流转只认这一个地方**，不要散落到各个 Service 里判断。
 *
 * <pre>
 * 创建中(CREATING) ──补账成功──> 待支付 ──支付──> 待发货 ──发货──> 待收货 ──确认收货──> 已完成
 *      └──补账失败/超时──> 创建失败（终态，可重新下单）
 *
 * 待支付 ──取消──> 已取消（终态）
 * 待发货 / 待收货 / 已完成 ──申请退款──> 退款中 ──同意──> 已退款（终态）
 *                                      └──驳回──> 回到申请前的状态
 * </pre>
 *
 * 待收货之后不能再取消（货已经出去了），只能走退款。
 * 已退款 / 已取消 / 已完成 是终态（已完成仍可申请退款 —— 相当于七天无理由）。
 * 创建中/创建失败是 M31 下单异步补账引入的状态：创建中不可支付、不可取消。
 */
public enum OrderStatus {

    /** 占位订单已落库，等待异步补账（扣 DB 库存 / 占券 / 清购物车） */
    CREATING("创建中"),
    /** 补账失败或超时；终态，用户需要重新下单 */
    CREATE_FAILED("创建失败"),
    PENDING_PAYMENT("待支付"),
    /** 已支付，等待商家发货 */
    PAID("待发货"),
    /** 已发货，等待买家确认收货 */
    SHIPPED("待收货"),
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
            case CREATING -> target == PENDING_PAYMENT || target == CREATE_FAILED;
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED || target == REFUNDING;
            case SHIPPED -> target == COMPLETED || target == REFUNDING;
            case COMPLETED -> target == REFUNDING;
            // 退款被驳回不是「流转到某个固定状态」，而是回到申请前那个状态，
            // 所以这里放行到 PAID / SHIPPED / COMPLETED，具体由 Service 校验一致性
            case REFUNDING -> target == REFUNDED || target == PAID
                    || target == SHIPPED || target == COMPLETED;
            case REFUNDED, CANCELLED, CREATE_FAILED -> false;
        };
    }

    /** 取消要回滚库存；只有「还没付款」的订单可以取消（已支付统一走退款/售后） */
    public boolean cancellable() {
        return this == PENDING_PAYMENT;
    }

    /** 可以申请退款的状态：付过钱、且还没退过 */
    public boolean refundable() {
        return this == PAID || this == SHIPPED || this == COMPLETED;
    }
}
