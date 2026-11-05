package com.tsuk1.itsmygo.dto;

import com.tsuk1.itsmygo.entity.AfterSaleItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 售后详情视图：售后单 + 明细 + 时间线 + 前端可执行的动作标记。 */
public record AfterSaleView(
        Long id,
        String afterSaleNo,
        Long orderId,
        String orderNo,
        Long userId,
        Long merchantId,
        String merchantName,
        String type,
        String typeLabel,
        String status,
        String statusLabel,
        String reasonCode,
        String reasonLabel,
        String reasonText,
        List<String> evidenceImages,
        BigDecimal applyAmount,
        BigDecimal refundAmount,
        String rejectReason,
        String returnTrackingNo,
        String returnCarrier,
        LocalDateTime reviewDeadline,
        LocalDateTime reviewedAt,
        LocalDateTime shippedBackAt,
        LocalDateTime receivedAt,
        LocalDateTime refundedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        List<AfterSaleItem> items,
        List<TimelineItem> timeline,
        boolean canApprove,
        boolean canReject,
        boolean canConfirmReceived,
        boolean canCancel,
        boolean canShipBack) {

    /** 时间线上的一条：时间 + 标题 + 说明。 */
    public record TimelineItem(LocalDateTime time, String title, String detail) {
    }
}
