package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.LogisticsProperties;
import com.tsuk1.itsmygo.dto.LogisticsView;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.Shipment;
import com.tsuk1.itsmygo.entity.ShipmentStatus;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.ShipmentMapper;
import com.tsuk1.itsmygo.service.ShipmentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 虚拟物流模拟。
 *
 * <p>核心约定：**eta 就是最终送达时间**。到点后状态必为 DELIVERED，
 * delivered_at 写 eta；不依赖任何定时任务或外部回调。
 * 状态按 shipped_at → eta 的进度推进：
 * 已揽收 → 运输中（25%）→ 派送中（80%）→ 已送达（100%）。
 */
@Service
public class ShipmentServiceImpl implements ShipmentService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> CARRIERS = List.of("买够速运", "云达快递", "极速物流");
    private static final List<Courier> COURIERS = List.of(
            new Courier("王师傅", "13800001231"),
            new Courier("李师傅", "13800001232"),
            new Courier("赵师傅", "13800001233"));
    private static final List<Origin> ORIGINS = List.of(
            new Origin("杭州转运中心", 120.155070, 30.274084),
            new Origin("上海转运中心", 121.473701, 31.230416),
            new Origin("深圳转运中心", 114.057868, 22.543099));

    private final ShipmentMapper shipmentMapper;
    private final MerchantMapper merchantMapper;
    private final LogisticsProperties properties;

    public ShipmentServiceImpl(ShipmentMapper shipmentMapper, MerchantMapper merchantMapper,
                               LogisticsProperties properties) {
        this.shipmentMapper = shipmentMapper;
        this.merchantMapper = merchantMapper;
        this.properties = properties;
    }

    @Override
    public void createForOrder(Order order) {
        if (order == null || order.getId() == null || find(order.getId()) != null) {
            return;
        }
        int seed = (int) Math.floorMod(order.getId(), (long) CARRIERS.size());
        Courier courier = COURIERS.get(seed);

        // 商家配了发货坐标就从商家发货；否则回退虚拟转运中心
        Merchant merchant = order.getMerchantId() == null
                ? null : merchantMapper.selectById(order.getMerchantId());
        boolean merchantLocated = merchant != null
                && merchant.getShipLng() != null && merchant.getShipLat() != null;
        String originName;
        double originLng;
        double originLat;
        if (merchantLocated) {
            originName = merchant.getName() + "发货仓";
            originLng = merchant.getShipLng().doubleValue();
            originLat = merchant.getShipLat().doubleValue();
        } else {
            Origin origin = ORIGINS.get(seed);
            originName = origin.name();
            originLng = origin.lng();
            originLat = origin.lat();
        }

        LocalDateTime shippedAt = order.getShippedAt() == null
                ? LocalDateTime.now() : order.getShippedAt();
        Shipment shipment = new Shipment();
        shipment.setOrderId(order.getId());
        shipment.setTrackingNo(StringUtils.hasText(order.getTrackingNo())
                ? order.getTrackingNo() : "MYG" + order.getOrderNo());
        shipment.setCarrier(CARRIERS.get(seed));
        shipment.setCourierName(courier.name());
        shipment.setCourierPhone(courier.phone());
        shipment.setOriginName(originName);
        shipment.setOriginLng(originLng);
        shipment.setOriginLat(originLat);
        shipment.setStatus(ShipmentStatus.PICKED_UP.name());
        shipment.setShippedAt(shippedAt);
        shipment.setEta(shippedAt.plusMinutes(properties.deliveryMinutes()));
        shipmentMapper.insert(shipment);
    }

    @Override
    public LogisticsView viewForOrder(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        Shipment shipment = find(order.getId());
        if (shipment == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shippedAt = shipment.getShippedAt();
        LocalDateTime eta = shipment.getEta();
        long totalSeconds = Math.max(1, Duration.between(shippedAt, eta).getSeconds());
        LocalDateTime inTransitAt = shippedAt.plusSeconds(Math.max(1, totalSeconds / 4));
        LocalDateTime outForDeliveryAt = eta.minusSeconds(Math.max(1, totalSeconds / 5));
        if (outForDeliveryAt.isBefore(inTransitAt)) {
            outForDeliveryAt = inTransitAt;
        }

        boolean cancelled = ShipmentStatus.CANCELLED.name().equals(shipment.getStatus());
        ShipmentStatus status;
        LocalDateTime deliveredAt = shipment.getDeliveredAt();
        if (cancelled) {
            status = ShipmentStatus.CANCELLED;
        } else if (!now.isBefore(eta)) {
            status = ShipmentStatus.DELIVERED;
            deliveredAt = eta;
        } else if (!now.isBefore(outForDeliveryAt)) {
            status = ShipmentStatus.OUT_FOR_DELIVERY;
        } else if (!now.isBefore(inTransitAt)) {
            status = ShipmentStatus.IN_TRANSIT;
        } else {
            status = ShipmentStatus.PICKED_UP;
        }

        if (!status.name().equals(shipment.getStatus())
                || (deliveredAt != null && !deliveredAt.equals(shipment.getDeliveredAt()))) {
            shipment.setStatus(status.name());
            shipment.setDeliveredAt(deliveredAt);
            shipmentMapper.updateById(shipment);
        }

        long elapsed = Math.max(0, Duration.between(shippedAt, now).getSeconds());
        int progress = (int) Math.min(100, elapsed * 100 / totalSeconds);

        List<LogisticsView.TimelineItem> timeline = List.of(
                item(ShipmentStatus.PICKED_UP, shippedAt, !now.isBefore(shippedAt)),
                item(ShipmentStatus.IN_TRANSIT, inTransitAt, !now.isBefore(inTransitAt)),
                item(ShipmentStatus.OUT_FOR_DELIVERY, outForDeliveryAt,
                        !now.isBefore(outForDeliveryAt)),
                item(ShipmentStatus.DELIVERED, eta, !now.isBefore(eta)));

        return new LogisticsView(
                nullToEmpty(order.getOrderNo()),
                nullToEmpty(shipment.getTrackingNo()),
                nullToEmpty(shipment.getCarrier()),
                nullToEmpty(shipment.getCourierName()),
                nullToEmpty(shipment.getCourierPhone()),
                status.name(),
                status.label(),
                format(shipment.getShippedAt()),
                format(eta),
                format(deliveredAt),
                progress,
                nullToEmpty(shipment.getOriginName()),
                shipment.getOriginLng() == null ? 0 : shipment.getOriginLng(),
                shipment.getOriginLat() == null ? 0 : shipment.getOriginLat(),
                nullToEmpty(order.getAddress()),
                timeline);
    }

    @Override
    public void cancelForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        shipmentMapper.update(null, Wrappers.<Shipment>lambdaUpdate()
                .eq(Shipment::getOrderId, orderId)
                .ne(Shipment::getStatus, ShipmentStatus.DELIVERED.name())
                .set(Shipment::getStatus, ShipmentStatus.CANCELLED.name()));
    }

    private Shipment find(Long orderId) {
        return shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getOrderId, orderId));
    }

    private LogisticsView.TimelineItem item(ShipmentStatus status,
                                            LocalDateTime time, boolean done) {
        return new LogisticsView.TimelineItem(
                status.name(), status.label(), format(time), done);
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Origin(String name, double lng, double lat) {
    }

    private record Courier(String name, String phone) {
    }
}
