package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付流水：一笔订单一条成功记录，退款后状态变 REFUNDED */
@Data
@TableName("payments")
public class Payment {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private Long orderId;

    /** 冗余订单号：排查问题时不用再 join orders */
    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    /** MOCK / ALIPAY / WECHAT —— 本项目只实现 MOCK */
    private String method;

    private String status;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
