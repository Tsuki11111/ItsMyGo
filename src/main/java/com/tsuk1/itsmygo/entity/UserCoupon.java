package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户券。领取时把券的条款快照下来，模板后续修改不影响已领的券。
 * 状态：UNUSED / USED / EXPIRED（EXPIRED 由查询时按 valid_to 计算，不落库）。
 */
@Data
@TableName("user_coupons")
public class UserCoupon {

    public static final String UNUSED = "UNUSED";
    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long couponId;

    /** 领取时的商户快照；NULL 表示平台券 */
    private Long merchantId;

    private String couponName;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    private String status;

    private Long usedOrderId;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 查询时按数据库 NOW() 计算的展示状态（EXPIRED 不入库） */
    @TableField(exist = false)
    private String displayStatus;
}
