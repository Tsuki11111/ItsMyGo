package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 虚拟物流运单：一个订单一张，发货时生成，状态按时间推进。 */
@Data
@TableName("shipments")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String trackingNo;

    private String carrier;

    private String courierName;

    private String courierPhone;

    private String originName;

    private Double originLng;

    private Double originLat;

    /** 见 {@link ShipmentStatus} */
    private String status;

    private LocalDateTime shippedAt;

    /** 预计送达时间 = 最终实际送达时间 */
    private LocalDateTime eta;

    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
