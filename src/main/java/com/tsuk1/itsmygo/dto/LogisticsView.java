package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 订单详情页的物流视图。坐标只给前端画地图用，不进入 agent 上下文。 */
public record LogisticsView(
        String orderNo,
        String trackingNo,
        String carrier,
        String courierName,
        String courierPhone,
        String status,
        String statusLabel,
        String shippedAt,
        String eta,
        String deliveredAt,
        int progress,
        String originName,
        double originLng,
        double originLat,
        String destAddress,
        List<TimelineItem> timeline) {

    public record TimelineItem(String status, String label, String time, boolean done) {
    }
}
