package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    /** 优惠前的商品总额；存量订单等于 totalAmount */
    private BigDecimal originalAmount;

    /** 优惠金额；没使用优惠券为 0 */
    private BigDecimal discountAmount;

    /** 使用的用户券 ID；仅作追溯，展示优惠读 couponName / discountAmount 快照 */
    private Long userCouponId;

    /** 券名称快照：券退回后可能被新订单复用，历史订单不能去 join user_coupons */
    private String couponName;

    /** 见 {@link OrderStatus}，库里存枚举名字符串 */
    private String status;

    /** 以下是下单时的收货信息快照，用户改地址不影响历史订单 */
    private String receiver;

    private String phone;

    private String address;

    private String trackingNo;

    /** 申请退款前的状态，仅 REFUNDING 期间有值；驳回时回退到它 */
    private String statusBeforeRefund;

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
