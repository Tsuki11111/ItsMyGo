package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单（M30）。
 *
 * <p>一个订单可以有多条售后（不同商品 / 不同时间），所以售后独立建表，
 * 不复用 orders 上的退款字段。状态机与金额口径见 docs/requirements.md §2.5。</p>
 */
@Data
@TableName("after_sales")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String afterSaleNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    /** REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
    private String type;

    /** PENDING_REVIEW / WAITING_RETURN / RETURN_SHIPPED / REFUNDED / REJECTED / CANCELLED */
    private String status;

    private String reasonCode;

    private String reasonText;

    /** 凭证图片 URL 的 JSON 数组 */
    private String evidenceImages;

    private BigDecimal applyAmount;

    private BigDecimal refundAmount;

    private String rejectReason;

    private String returnTrackingNo;

    private String returnCarrier;

    private LocalDateTime reviewDeadline;

    private LocalDateTime reviewedAt;

    private LocalDateTime shippedBackAt;

    private LocalDateTime receivedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
