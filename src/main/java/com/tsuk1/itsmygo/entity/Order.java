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
