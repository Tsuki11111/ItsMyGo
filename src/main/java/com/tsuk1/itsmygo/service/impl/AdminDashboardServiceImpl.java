package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminDashboardView;
import com.tsuk1.itsmygo.dto.AdminLowStockSku;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.AdminDashboardMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.AdminDashboardService;
import com.tsuk1.itsmygo.service.MerchantService;
import com.tsuk1.itsmygo.service.OrderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int PENDING_LIMIT = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AdminDashboardMapper dashboardMapper;
    private final OrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderService orderService;
    private final MerchantService merchantService;

    public AdminDashboardServiceImpl(AdminDashboardMapper dashboardMapper,
                                     OrderMapper orderMapper,
                                     MerchantMapper merchantMapper,
                                     UserMapper userMapper,
                                     ProductMapper productMapper,
                                     OrderService orderService,
                                     MerchantService merchantService) {
        this.dashboardMapper = dashboardMapper;
        this.orderMapper = orderMapper;
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderService = orderService;
        this.merchantService = merchantService;
    }

    @Override
    public AdminDashboardView dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        BigDecimal gmv = dashboardMapper.sumPaidAmount(start, end);
        long todayOrders = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .ge(Order::getCreatedAt, start).lt(Order::getCreatedAt, end));
        long todayPaidOrders = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .ge(Order::getPaidAt, start).lt(Order::getPaidAt, end));
        long pendingShipments = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PAID.name()));
        long pendingRefunds = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.REFUNDING.name()));
        long pendingMerchants = merchantMapper.selectCount(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getStatus, Merchant.PENDING));
        long lowStockSkus = dashboardMapper.countLowStock(LOW_STOCK_THRESHOLD);
        long totalUsers = userMapper.selectCount(null);
        long activeMerchants = merchantMapper.selectCount(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getStatus, Merchant.ACTIVE));
        long onSaleProducts = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1));

        return new AdminDashboardView(
                money(gmv),
                todayOrders,
                todayPaidOrders,
                pendingShipments,
                pendingRefunds,
                pendingMerchants,
                lowStockSkus,
                totalUsers,
                activeMerchants,
                onSaleProducts,
                trend(today),
                pendingOrders(OrderStatus.PAID.name()),
                pendingOrders(OrderStatus.REFUNDING.name()),
                pendingMerchants(),
                lowStock());
    }

    private List<AdminDashboardView.TrendPoint> trend(LocalDate today) {
        LocalDate first = today.minusDays(6);
        Map<LocalDate, AdminDailySales> byDay = dashboardMapper
                .dailySales(first.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(AdminDailySales::getDay, Function.identity()));
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(i -> {
                    LocalDate day = first.plusDays(i);
                    AdminDailySales row = byDay.get(day);
                    return new AdminDashboardView.TrendPoint(
                            day.toString(),
                            money(row == null ? null : row.getAmount()),
                            row == null || row.getOrderCount() == null ? 0 : row.getOrderCount());
                })
                .toList();
    }

    private List<AdminDashboardView.PendingOrder> pendingOrders(String status) {
        return orderService.pageForAdmin(1, PENDING_LIMIT, status, null).records().stream()
                .map(order -> new AdminDashboardView.PendingOrder(
                        order.getId(),
                        order.getOrderNo(),
                        nullToEmpty(order.getMerchantName()),
                        money(order.getTotalAmount()),
                        statusLabel(order.getStatus()),
                        format(order.getCreatedAt())))
                .toList();
    }

    private List<AdminDashboardView.PendingMerchant> pendingMerchants() {
        return merchantService.pageForAdmin(1, PENDING_LIMIT, Merchant.PENDING, null)
                .records().stream()
                .map(merchant -> new AdminDashboardView.PendingMerchant(
                        merchant.id(), merchant.name(), merchant.contactName(),
                        format(merchant.createdAt())))
                .toList();
    }

    private List<AdminDashboardView.LowStock> lowStock() {
        return dashboardMapper.lowStock(LOW_STOCK_THRESHOLD, PENDING_LIMIT).stream()
                .map(this::lowStockView)
                .toList();
    }

    private AdminDashboardView.LowStock lowStockView(AdminLowStockSku sku) {
        return new AdminDashboardView.LowStock(
                sku.getProductId(), nullToEmpty(sku.getProductName()),
                nullToEmpty(sku.getImage()), sku.getSkuId(),
                nullToEmpty(sku.getSpecText()), sku.getStock());
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.valueOf(status).label();
        } catch (Exception e) {
            return status;
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00"
                : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
