package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券模板（满减券，全场通用） */
@Data
@TableName("coupons")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 满多少可用 */
    private BigDecimal thresholdAmount;

    /** 减多少 */
    private BigDecimal discountAmount;

    /** 发放总量 */
    private Integer totalCount;

    /** 已领取数 */
    private Integer claimedCount;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    /** 1 可领 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
