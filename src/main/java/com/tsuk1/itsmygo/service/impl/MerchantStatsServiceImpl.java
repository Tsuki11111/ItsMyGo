package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminDashboardView;
import com.tsuk1.itsmygo.dto.AdminLowStockSku;
import com.tsuk1.itsmygo.dto.AdminMerchantInventoryRow;
import com.tsuk1.itsmygo.dto.AdminTopProductRow;
import com.tsuk1.itsmygo.dto.MerchantDashboardView;
import com.tsuk1.itsmygo.dto.MerchantStatsSummary;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.MerchantStatsMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.service.MerchantStatsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 商家后台经营看板。
 *
 * 所有查询都按登录态的 merchantId 过滤；金额只用于展示，不参与任何判定。
 */
@Service
public class MerchantStatsServiceImpl implements MerchantStatsService {

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int LOW_STOCK_LIMIT = 6;
    private static final int TOP_PRODUCT_LIMIT = 5;
    private static final int RECENT_ORDER_LIMIT = 5;

    private final MerchantStatsMapper statsMapper;
    private final MerchantMapper merchantMapper;
    private final OrderMapper orderMapper;

    public MerchantStatsServiceImpl(MerchantStatsMapper statsMapper, MerchantMapper merchantMapper,
                                    OrderMapper orderMapper) {
        this.statsMapper = statsMapper;
        this.merchantMapper = merchantMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public MerchantDashboardView dashboard(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime start30 = today.minusDays(29).atStartOfDay();

        MerchantStatsSummary summary = statsMapper.summary(merchantId, start30, todayStart, end);
        AdminMerchantInventoryRow inventory = statsMapper.inventory(merchantId, LOW_STOCK_THRESHOLD);

        return new MerchantDashboardView(
                nullToEmpty(merchant.getName()),
                money(summary == null ? null : summary.getTodayGmv()),
                value(summary == null ? null : summary.getTodayOrders()),
                money(summary == null ? null : summary.getGmv30()),
                value(summary == null ? null : summary.getPaidOrders30()),
                value(summary == null ? null : summary.getOrders30()),
                value(summary == null ? null : summary.getRefunding30()),
                average(summary == null ? null : summary.getGmv30(),
                        summary == null ? null : summary.getPaidOrders30()),
                value(summary == null ? null : summary.getPendingShipment()),
                value(summary == null ? null : summary.getPendingRefund()),
                inventory == null ? 0 : value(inventory.getOnSaleProducts()),
                inventory == null ? 0 : value(inventory.getLowStockSkus()),
                trend(merchantId, today),
                topProducts(merchantId),
                lowStock(merchantId),
                recentOrders(merchantId));
    }

    private List<AdminDashboardView.TrendPoint> trend(Long merchantId, LocalDate today) {
        LocalDate first = today.minusDays(6);
        Map<LocalDate, AdminDailySales> byDay = statsMapper
                .dailySales(merchantId, first.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream().collect(Collectors.toMap(AdminDailySales::getDay, Function.identity()));
        return IntStream.rangeClosed(0, 6).mapToObj(i -> {
            LocalDate day = first.plusDays(i);
            AdminDailySales row = byDay.get(day);
            return new AdminDashboardView.TrendPoint(
                    day.toString(),
                    money(row == null ? null : row.getAmount()),
                    row == null || row.getOrderCount() == null ? 0 : row.getOrderCount());
        }).toList();
    }

    private List<MerchantDashboardView.TopProduct> topProducts(Long merchantId) {
        return statsMapper.topProducts(merchantId, TOP_PRODUCT_LIMIT).stream()
                .map(row -> new MerchantDashboardView.TopProduct(
                        row.getProductId(), nullToEmpty(row.getName()),
                        nullToEmpty(row.getImage()), money(row.getPrice()),
                        row.getStock() == null ? 0 : row.getStock(),
                        row.getSales() == null ? 0 : row.getSales()))
                .toList();
    }

    private List<MerchantDashboardView.LowStock> lowStock(Long merchantId) {
        return statsMapper.lowStock(merchantId, LOW_STOCK_THRESHOLD, LOW_STOCK_LIMIT).stream()
                .map(row -> new MerchantDashboardView.LowStock(
                        row.getProductId(), nullToEmpty(row.getProductName()),
                        nullToEmpty(row.getImage()), row.getSkuId(),
                        nullToEmpty(row.getSpecText()),
                        row.getStock() == null ? 0 : row.getStock()))
                .toList();
    }

    private List<MerchantDashboardView.RecentOrder> recentOrders(Long merchantId) {
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                        .eq(Order::getMerchantId, merchantId)
                        .orderByDesc(Order::getId)
                        .last("LIMIT " + RECENT_ORDER_LIMIT)).stream()
                .map(order -> new MerchantDashboardView.RecentOrder(
                        order.getId(), nullToEmpty(order.getOrderNo()),
                        money(order.getTotalAmount()), nullToEmpty(order.getStatus()),
                        statusLabel(order.getStatus()), format(order.getCreatedAt())))
                .toList();
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.valueOf(status).label();
        } catch (Exception e) {
            return nullToEmpty(status);
        }
    }

    private String average(BigDecimal amount, Long count) {
        if (amount == null || count == null || count <= 0) {
            return "0.00";
        }
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00"
                : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
