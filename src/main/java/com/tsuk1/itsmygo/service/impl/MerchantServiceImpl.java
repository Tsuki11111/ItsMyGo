package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminDashboardView;
import com.tsuk1.itsmygo.dto.AdminMerchantInventoryRow;
import com.tsuk1.itsmygo.dto.AdminMerchantOperationsView;
import com.tsuk1.itsmygo.dto.AdminMerchantSalesRow;
import com.tsuk1.itsmygo.dto.AdminMerchantView;
import com.tsuk1.itsmygo.dto.AdminTopProductRow;
import com.tsuk1.itsmygo.dto.MerchantRequest;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipAddressRequest;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AdminOperationsMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.common.identity.AuthIdentityService;
import com.tsuk1.itsmygo.service.MerchantService;
import com.tsuk1.itsmygo.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class MerchantServiceImpl implements MerchantService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int TOP_PRODUCT_LIMIT = 5;
    private static final int RECENT_ORDER_LIMIT = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AdminOperationsMapper operationsMapper;
    private final AuthIdentityService authIdentityService;
    private final NotificationService notificationService;

    public MerchantServiceImpl(MerchantMapper merchantMapper, UserMapper userMapper,
                               OrderMapper orderMapper, AdminOperationsMapper operationsMapper,
                               AuthIdentityService authIdentityService,
                               NotificationService notificationService) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.operationsMapper = operationsMapper;
        this.authIdentityService = authIdentityService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public MerchantView apply(Long userId, MerchantRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (Roles.MERCHANT.equals(user.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该账号已是商家");
        }

        Merchant existing = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId));
        if (existing != null) {
            if (Merchant.PENDING.equals(existing.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "入驻申请审核中");
            }
            if (Merchant.ACTIVE.equals(existing.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "该账号已是商家");
            }
            if (Merchant.DISABLED.equals(existing.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "店铺已被禁用");
            }
            return resubmit(existing.getId(), request);
        }

        Merchant merchant = new Merchant();
        applyFields(merchant, request);
        merchant.setStatus(Merchant.PENDING);
        merchant.setOwnerUserId(userId);
        merchant.setRejectReason("");
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺名已被占用");
        }
        notifyAdminsMerchantApply(merchant);
        return toView(merchant);
    }

    private MerchantView resubmit(Long merchantId, MerchantRequest request) {
        Merchant update = new Merchant();
        applyFields(update, request);
        update.setStatus(Merchant.PENDING);
        update.setRejectReason("");
        int rows = merchantMapper.update(update, Wrappers.<Merchant>lambdaUpdate()
                .eq(Merchant::getId, merchantId)
                .eq(Merchant::getStatus, Merchant.REJECTED));
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }
        Merchant updated = merchantMapper.selectById(merchantId);
        notifyAdminsMerchantApply(updated);
        return toView(updated);
    }

    /** 入驻申请/重新提交 → 通知全部管理员 */
    private void notifyAdminsMerchantApply(Merchant merchant) {
        if (merchant == null) {
            return;
        }
        try {
            notificationService.notifyAdmins("MERCHANT_APPLY", "商家入驻待审核",
                    String.format("店铺「%s」提交了入驻申请，请审核。", merchant.getName()),
                    "/admin/merchants?merchantId=" + merchant.getId(), null);
        } catch (RuntimeException e) {
            log.warn("店铺 {} 入驻申请写通知失败: {}", merchant.getId(), e.getMessage());
        }
    }

    @Override
    public MerchantView myApplication(Long userId) {
        Merchant merchant = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getOwnerUserId, userId));
        return merchant == null ? null : toView(merchant);
    }

    @Override
    public MerchantView publicShop(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在");
        }
        // 公开店铺页不能泄露发货仓地址 / 坐标
        return toView(merchant, false);
    }

    @Override
    public MerchantView profile(Long merchantId) {
        return toView(requireActive(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateProfile(Long merchantId, MerchantRequest request) {
        Merchant merchant = requireActive(merchantId);
        Merchant update = new Merchant();
        applyFields(update, request);
        try {
            merchantMapper.update(update, Wrappers.<Merchant>lambdaUpdate()
                    .eq(Merchant::getId, merchant.getId()));
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺名已被占用");
        }
        return toView(merchantMapper.selectById(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateShipAddress(Long merchantId, ShipAddressRequest request) {
        requireMerchant(merchantId);
        // 经纬度必须成对；缺一个就清空，物流回退虚拟转运中心
        BigDecimal lng = request.lng();
        BigDecimal lat = request.lat();
        if (lng == null || lat == null) {
            lng = null;
            lat = null;
        }
        merchantMapper.update(null, Wrappers.<Merchant>lambdaUpdate()
                .eq(Merchant::getId, merchantId)
                .set(Merchant::getShipProvince, trim(request.province()))
                .set(Merchant::getShipCity, trim(request.city()))
                .set(Merchant::getShipDistrict, trim(request.district()))
                .set(Merchant::getShipAddress, trim(request.address()))
                .set(Merchant::getShipLng, lng)
                .set(Merchant::getShipLat, lat));
        return toView(merchantMapper.selectById(merchantId));
    }

    @Override
    public PageResponse<AdminMerchantView> pageForAdmin(int page, int size, String status, String keyword) {
        Page<Merchant> result = merchantMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Merchant>lambdaQuery()
                        .eq(StringUtils.hasText(status), Merchant::getStatus, status)
                        .like(StringUtils.hasText(keyword), Merchant::getName, keyword)
                        .orderByDesc(Merchant::getId));

        List<Merchant> merchants = result.getRecords();
        Set<Long> ownerIds = new HashSet<>();
        List<Long> merchantIds = new ArrayList<>(merchants.size());
        for (Merchant merchant : merchants) {
            if (merchant.getOwnerUserId() != null) {
                ownerIds.add(merchant.getOwnerUserId());
            }
            merchantIds.add(merchant.getId());
        }
        Map<Long, User> users = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (User user : userMapper.selectByIds(ownerIds)) {
                users.put(user.getId(), user);
            }
        }

        List<AdminMerchantView> views = new ArrayList<>(merchants.size());
        for (Merchant merchant : merchants) {
            User owner = merchant.getOwnerUserId() == null
                    ? null : users.get(merchant.getOwnerUserId());
            views.add(toAdminView(merchant, owner));
        }

        if (!merchants.isEmpty()) {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.minusDays(29).atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay();
            // 30 天销售 / 库存统计各查一次，再按 merchantId 回填到视图
            Map<Long, AdminMerchantSalesRow> sales = new HashMap<>();
            for (AdminMerchantSalesRow row
                    : operationsMapper.merchantSales(merchantIds, start, end)) {
                sales.put(row.getMerchantId(), row);
            }
            Map<Long, AdminMerchantInventoryRow> inventory = new HashMap<>();
            for (AdminMerchantInventoryRow row
                    : operationsMapper.merchantInventory(merchantIds, LOW_STOCK_THRESHOLD)) {
                inventory.put(row.getMerchantId(), row);
            }
            List<AdminMerchantView> enrichedViews = new ArrayList<>(views.size());
            for (AdminMerchantView view : views) {
                AdminMerchantSalesRow sale = sales.get(view.id());
                AdminMerchantInventoryRow stock = inventory.get(view.id());
                enrichedViews.add(view.withStats(
                        money(sale == null ? null : sale.getGmv30()),
                        sale == null || sale.getOrders30() == null ? 0 : sale.getOrders30(),
                        sale == null || sale.getRefunding30() == null ? 0 : sale.getRefunding30(),
                        stock == null || stock.getOnSaleProducts() == null
                                ? 0 : stock.getOnSaleProducts(),
                        stock == null || stock.getLowStockSkus() == null
                                ? 0 : stock.getLowStockSkus()));
            }
            views = enrichedViews;
        }
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AdminMerchantOperationsView operationsForAdmin(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        User owner = merchant.getOwnerUserId() == null ? null
                : userMapper.selectById(merchant.getOwnerUserId());
        AdminMerchantView base = toAdminView(merchant, owner);

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(29).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        AdminMerchantSalesRow sale = first(operationsMapper
                .merchantSales(List.of(merchantId), start, end));
        AdminMerchantInventoryRow stock = first(operationsMapper
                .merchantInventory(List.of(merchantId), LOW_STOCK_THRESHOLD));
        AdminMerchantView enriched = base.withStats(
                money(sale == null ? null : sale.getGmv30()),
                sale == null || sale.getOrders30() == null ? 0 : sale.getOrders30(),
                sale == null || sale.getRefunding30() == null ? 0 : sale.getRefunding30(),
                stock == null || stock.getOnSaleProducts() == null
                        ? 0 : stock.getOnSaleProducts(),
                stock == null || stock.getLowStockSkus() == null
                        ? 0 : stock.getLowStockSkus());

        return new AdminMerchantOperationsView(
                enriched,
                enriched.gmv30(), enriched.orders30(), enriched.refunding30(),
                enriched.onSaleProducts(), enriched.lowStockSkus(),
                trend(merchantId, today),
                topProducts(merchantId),
                recentOrders(merchantId));
    }

    private List<AdminDashboardView.TrendPoint> trend(Long merchantId, LocalDate today) {
        LocalDate first = today.minusDays(6);
        // 数据库只会返回有销量的日期，这里补齐 7 天，没有数据的补 0
        Map<LocalDate, AdminDailySales> byDay = new HashMap<>();
        for (AdminDailySales row : operationsMapper.merchantDailySales(
                merchantId, first.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            byDay.put(row.getDay(), row);
        }
        List<AdminDashboardView.TrendPoint> points = new ArrayList<>(7);
        for (int i = 0; i <= 6; i++) {
            LocalDate day = first.plusDays(i);
            AdminDailySales row = byDay.get(day);
            points.add(new AdminDashboardView.TrendPoint(
                    day.toString(),
                    money(row == null ? null : row.getAmount()),
                    row == null || row.getOrderCount() == null ? 0 : row.getOrderCount()));
        }
        return points;
    }

    private List<AdminMerchantOperationsView.TopProduct> topProducts(Long merchantId) {
        List<AdminMerchantOperationsView.TopProduct> products = new ArrayList<>();
        for (AdminTopProductRow row : operationsMapper.topProducts(merchantId, TOP_PRODUCT_LIMIT)) {
            products.add(new AdminMerchantOperationsView.TopProduct(
                    row.getProductId(), nullToEmpty(row.getName()),
                    nullToEmpty(row.getImage()), money(row.getPrice()),
                    row.getStock() == null ? 0 : row.getStock(),
                    row.getSales() == null ? 0 : row.getSales()));
        }
        return products;
    }

    private List<AdminMerchantOperationsView.RecentOrder> recentOrders(Long merchantId) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getMerchantId, merchantId)
                .orderByDesc(Order::getId)
                .last("LIMIT " + RECENT_ORDER_LIMIT));
        List<AdminMerchantOperationsView.RecentOrder> views = new ArrayList<>(orders.size());
        for (Order order : orders) {
            views.add(new AdminMerchantOperationsView.RecentOrder(
                    order.getId(), nullToEmpty(order.getOrderNo()),
                    money(order.getTotalAmount()), nullToEmpty(order.getStatus()),
                    statusLabel(order.getStatus()), format(order.getCreatedAt())));
        }
        return views;
    }

    private <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.valueOf(status).label();
        } catch (Exception e) {
            return nullToEmpty(status);
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

    @Override
    @Transactional
    public void approve(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能审核");
        }
        if (!Merchant.PENDING.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请不在待审核状态");
        }
        Long ownerId = merchant.getOwnerUserId();
        if (ownerId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请缺少店主账号");
        }
        User owner = userMapper.selectById(ownerId);
        if (owner == null || Integer.valueOf(0).equals(owner.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "店主账号不存在或已被禁用");
        }
        if (owner.getMerchantId() != null && !merchantId.equals(owner.getMerchantId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该账号已绑定其他店铺");
        }
        if (merchantMapper.approveIfPending(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }

        User update = new User();
        update.setId(ownerId);
        update.setRole(Roles.MERCHANT);
        update.setMerchantId(merchantId);
        userMapper.updateById(update);
        authIdentityService.evict(ownerId);
    }

    @Override
    @Transactional
    public void reject(Long merchantId, String reason) {
        Merchant merchant = requireMerchant(merchantId);
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能审核");
        }
        if (!Merchant.PENDING.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请不在待审核状态");
        }
        if (merchantMapper.rejectIfPending(merchantId, reason) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请状态已变化，请刷新后重试");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    @Transactional
    public void disable(Long merchantId) {
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能禁用");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (merchantMapper.disableIfActive(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有营业中的店铺可以禁用");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    @Transactional
    public void enable(Long merchantId) {
        if (Merchant.PLATFORM_MERCHANT_ID == merchantId) {
            throw new BizException(ResultCode.BAD_REQUEST, "平台自营店铺不能操作");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (merchantMapper.enableIfDisabled(merchantId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已禁用的店铺可以启用");
        }
        authIdentityService.evict(merchant.getOwnerUserId());
    }

    @Override
    public Merchant requireActive(Long merchantId) {
        Merchant merchant = merchantId == null ? null : merchantMapper.selectById(merchantId);
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在或已停用");
        }
        return merchant;
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在");
        }
        return merchant;
    }

    private void applyFields(Merchant merchant, MerchantRequest request) {
        merchant.setName(request.name().trim());
        merchant.setLogo(request.logo() == null ? "" : request.logo());
        merchant.setDescription(request.description() == null ? "" : request.description());
        merchant.setContactName(request.contactName().trim());
        merchant.setContactPhone(request.contactPhone().trim());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private MerchantView toView(Merchant merchant) {
        return toView(merchant, true);
    }

    private MerchantView toView(Merchant merchant, boolean includeShipAddress) {
        return new MerchantView(merchant.getId(), merchant.getName(), merchant.getLogo(),
                merchant.getDescription(), merchant.getContactName(), merchant.getContactPhone(),
                includeShipAddress ? merchant.getShipProvince() : "",
                includeShipAddress ? merchant.getShipCity() : "",
                includeShipAddress ? merchant.getShipDistrict() : "",
                includeShipAddress ? merchant.getShipAddress() : "",
                includeShipAddress ? merchant.getShipLng() : null,
                includeShipAddress ? merchant.getShipLat() : null,
                merchant.getStatus(), merchant.getRejectReason(), merchant.getOwnerUserId(),
                merchant.getCreatedAt());
    }

    private AdminMerchantView toAdminView(Merchant merchant, User owner) {
        return new AdminMerchantView(merchant.getId(), merchant.getName(), merchant.getLogo(),
                merchant.getDescription(), merchant.getContactName(), merchant.getContactPhone(),
                merchant.getShipProvince(), merchant.getShipCity(), merchant.getShipDistrict(),
                merchant.getShipAddress(), merchant.getShipLng(), merchant.getShipLat(),
                merchant.getStatus(), merchant.getRejectReason(), merchant.getOwnerUserId(),
                owner == null ? "" : owner.getUsername(), merchant.getCreatedAt(),
                "0.00", 0, 0, 0, 0);
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
