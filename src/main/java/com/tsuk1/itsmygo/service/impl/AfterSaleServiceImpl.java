package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.after_sale.AfterSaleProperties;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AfterSaleApplyRequest;
import com.tsuk1.itsmygo.dto.AfterSaleItemRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonView;
import com.tsuk1.itsmygo.dto.AfterSaleRejectRequest;
import com.tsuk1.itsmygo.dto.AfterSaleShipRequest;
import com.tsuk1.itsmygo.dto.AfterSaleStatsView;
import com.tsuk1.itsmygo.dto.AfterSaleView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.AfterSale;
import com.tsuk1.itsmygo.entity.AfterSaleItem;
import com.tsuk1.itsmygo.entity.AfterSaleReason;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.mapper.AfterSaleItemMapper;
import com.tsuk1.itsmygo.mapper.AfterSaleMapper;
import com.tsuk1.itsmygo.mapper.AfterSaleReasonMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.AfterSaleService;
import com.tsuk1.itsmygo.service.NotificationService;
import com.tsuk1.itsmygo.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后单实现（M30）。
 *
 * <p>三个必须守住的口径：</p>
 * <ol>
 *   <li><b>金额</b>：项金额 = 单价 × 数量，优惠按订单等比分摊，尾差归最后一次退款；</li>
 *   <li><b>券</b>：只有订单全部退完才退回（部分退款不退券）；</li>
 *   <li><b>状态</b>：所有流转都走 {@code compareAndSetStatus}，并发重复操作只成功一次。</li>
 * </ol>
 */
@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {

    private static final String TYPE_REFUND_ONLY = "REFUND_ONLY";
    private static final String TYPE_RETURN_REFUND = "RETURN_REFUND";

    private static final String ST_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ST_WAITING_RETURN = "WAITING_RETURN";
    private static final String ST_RETURN_SHIPPED = "RETURN_SHIPPED";
    private static final String ST_REFUNDED = "REFUNDED";
    private static final String ST_REJECTED = "REJECTED";
    private static final String ST_CANCELLED = "CANCELLED";

    private static final Map<String, String> TYPE_LABELS = Map.of(
            TYPE_REFUND_ONLY, "仅退款",
            TYPE_RETURN_REFUND, "退货退款");

    private static final Map<String, String> STATUS_LABELS = Map.of(
            ST_PENDING_REVIEW, "待审核",
            ST_WAITING_RETURN, "待买家退货",
            ST_RETURN_SHIPPED, "待商家收货",
            ST_REFUNDED, "已退款",
            ST_REJECTED, "已驳回",
            ST_CANCELLED, "已撤销");

    private static final List<String> ALL_STATUSES = List.of(
            ST_PENDING_REVIEW, ST_WAITING_RETURN, ST_RETURN_SHIPPED, ST_REFUNDED, ST_REJECTED, ST_CANCELLED);

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final AfterSaleReasonMapper reasonMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final PaymentMapper paymentMapper;
    private final UserCouponMapper userCouponMapper;
    private final MerchantMapper merchantMapper;
    private final NotificationService notificationService;
    private final StockService stockService;
    private final AfterSaleProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AfterSaleServiceImpl(AfterSaleMapper afterSaleMapper,
                                AfterSaleItemMapper afterSaleItemMapper,
                                AfterSaleReasonMapper reasonMapper,
                                OrderMapper orderMapper,
                                OrderItemMapper orderItemMapper,
                                ProductSkuMapper skuMapper,
                                ProductMapper productMapper,
                                PaymentMapper paymentMapper,
                                UserCouponMapper userCouponMapper,
                                MerchantMapper merchantMapper,
                                StockService stockService,
                                AfterSaleProperties properties,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager,
                                NotificationService notificationService) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.reasonMapper = reasonMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.paymentMapper = paymentMapper;
        this.userCouponMapper = userCouponMapper;
        this.merchantMapper = merchantMapper;
        this.stockService = stockService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationService = notificationService;
    }

    /* ==================== 顾客 ==================== */

    @Override
    public List<AfterSaleReasonView> reasons() {
        return reasonMapper.selectList(Wrappers.<AfterSaleReason>lambdaQuery()
                        .eq(AfterSaleReason::getStatus, 1)
                        .orderByAsc(AfterSaleReason::getSort)
                        .orderByAsc(AfterSaleReason::getId))
                .stream().map(this::reasonView).toList();
    }

    @Override
    @Transactional
    public AfterSaleView apply(Long userId, AfterSaleApplyRequest request) {
        String type = normalizeType(request.type());
        Order order = orderMapper.selectByIdForUpdate(request.orderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!isRefundableStatus(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前订单状态不能申请售后");
        }
        if (TYPE_RETURN_REFUND.equals(type)
                && !(OrderStatusValue.SHIPPED.equals(order.getStatus())
                || OrderStatusValue.COMPLETED.equals(order.getStatus()))) {
            throw new BizException(ResultCode.BAD_REQUEST, "未发货订单只能申请仅退款");
        }

        AfterSaleReason reason = reasonMapper.selectOne(Wrappers.<AfterSaleReason>lambdaQuery()
                .eq(AfterSaleReason::getCode, request.reasonCode())
                .eq(AfterSaleReason::getStatus, 1));
        if (reason == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "售后原因不存在或已停用");
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()));
        if (orderItems.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单没有可售后的商品");
        }
        Plan plan = plan(order, orderItems, request.items());

        AfterSale afterSale = new AfterSale();
        afterSale.setAfterSaleNo(generateNo());
        afterSale.setOrderId(order.getId());
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setUserId(userId);
        afterSale.setMerchantId(order.getMerchantId());
        afterSale.setType(type);
        afterSale.setStatus(ST_PENDING_REVIEW);
        afterSale.setReasonCode(reason.getCode());
        afterSale.setReasonText(nullToEmpty(request.reasonText()));
        String imagesJson = writeImages(request.evidenceImages());
        if (imagesJson.length() > 1000) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证图片过多，请精简后再提交");
        }
        afterSale.setEvidenceImages(imagesJson);
        afterSale.setApplyAmount(plan.total());
        afterSale.setRefundAmount(ZERO);
        afterSale.setReviewDeadline(LocalDateTime.now().plusHours(properties.reviewHours()));
        afterSaleMapper.insert(afterSale);

        for (AfterSaleItem item : plan.items()) {
            item.setAfterSaleId(afterSale.getId());
            afterSaleItemMapper.insert(item);
        }
        notificationService.notifyMerchant(afterSale.getMerchantId(), "AFTER_SALE_APPLIED", "新售后申请",
                String.format("售后单 %s（订单 %s）待处理，申请金额 ¥%s。",
                        afterSale.getAfterSaleNo(), afterSale.getOrderNo(), afterSale.getApplyAmount()),
                "/merchant/after-sales?afterSaleId=" + afterSale.getId(),
                "/admin/after-sales?afterSaleId=" + afterSale.getId(),
                "after-sale-applied:" + afterSale.getAfterSaleNo());
        return view(afterSale, plan.items(), null);
    }

    @Override
    public PageResponse<AfterSaleView> pageForCustomer(Long userId, int page, int size, String status, Long orderId) {
        return page(Wrappers.<AfterSale>lambdaQuery()
                        .eq(AfterSale::getUserId, userId)
                        .eq(orderId != null, AfterSale::getOrderId, orderId)
                        .eq(status != null && !status.isBlank(), AfterSale::getStatus, status),
                page, size);
    }

    @Override
    public AfterSaleView detailForCustomer(Long userId, Long id) {
        AfterSale afterSale = requireExists(id);
        if (!userId.equals(afterSale.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "售后单不存在");
        }
        return view(afterSale, itemsOf(id), null);
    }

    @Override
    @Transactional
    public AfterSaleView cancel(Long userId, Long id) {
        AfterSale afterSale = requireExists(id);
        if (!userId.equals(afterSale.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "售后单不存在");
        }
        String from;
        if (ST_PENDING_REVIEW.equals(afterSale.getStatus()) || ST_WAITING_RETURN.equals(afterSale.getStatus())) {
            from = afterSale.getStatus();
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "当前售后状态不能撤销");
        }
        cas(afterSale, from, ST_CANCELLED);
        afterSale.setStatus(ST_CANCELLED);
        afterSale.setCancelledAt(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        return view(afterSale, itemsOf(id), null);
    }

    @Override
    @Transactional
    public AfterSaleView shipBack(Long userId, Long id, AfterSaleShipRequest request) {
        AfterSale afterSale = requireExists(id);
        if (!userId.equals(afterSale.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "售后单不存在");
        }
        cas(afterSale, ST_WAITING_RETURN, ST_RETURN_SHIPPED);
        afterSale.setStatus(ST_RETURN_SHIPPED);
        afterSale.setReturnTrackingNo(request.trackingNo().trim());
        afterSale.setReturnCarrier(nullToEmpty(request.carrier()));
        afterSale.setShippedBackAt(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        return view(afterSale, itemsOf(id), null);
    }

    /* ==================== 商家 ==================== */

    @Override
    public PageResponse<AfterSaleView> pageForMerchant(Long merchantId, int page, int size, String status) {
        return page(Wrappers.<AfterSale>lambdaQuery()
                        .eq(AfterSale::getMerchantId, merchantId)
                        .eq(status != null && !status.isBlank(), AfterSale::getStatus, status),
                page, size);
    }

    @Override
    public AfterSaleView detailForMerchant(Long merchantId, Long id) {
        return view(requireOwnedByMerchant(merchantId, id), itemsOf(id), null);
    }

    @Override
    @Transactional
    public AfterSaleView approveForMerchant(Long merchantId, Long id) {
        return approve(requireOwnedByMerchant(merchantId, id), null);
    }

    @Override
    @Transactional
    public AfterSaleView rejectForMerchant(Long merchantId, Long id, AfterSaleRejectRequest request) {
        return reject(requireOwnedByMerchant(merchantId, id), request.reason());
    }

    @Override
    @Transactional
    public AfterSaleView confirmReceivedForMerchant(Long merchantId, Long id) {
        return confirmReceived(requireOwnedByMerchant(merchantId, id));
    }

    @Override
    public AfterSaleStatsView statsForMerchant(Long merchantId) {
        return stats(afterSaleMapper.countByMerchant(merchantId));
    }

    /* ==================== 管理端 ==================== */

    @Override
    public PageResponse<AfterSaleView> pageForAdmin(int page, int size, String status, String keyword) {
        var wrapper = Wrappers.<AfterSale>lambdaQuery()
                .eq(status != null && !status.isBlank(), AfterSale::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AfterSale::getAfterSaleNo, kw).or().like(AfterSale::getOrderNo, kw));
        }
        return page(wrapper, page, size);
    }

    @Override
    public AfterSaleView detailForAdmin(Long id) {
        return view(requireExists(id), itemsOf(id), null);
    }

    @Override
    @Transactional
    public AfterSaleView approveForAdmin(Long id) {
        AfterSale afterSale = requireExists(id);
        AfterSaleView result = approve(afterSale, null);
        notifyAfterSaleResult(afterSale, "平台已同意售后");
        return result;
    }

    @Override
    @Transactional
    public AfterSaleView rejectForAdmin(Long id, AfterSaleRejectRequest request) {
        return reject(requireExists(id), request.reason());
    }

    @Override
    @Transactional
    public AfterSaleView confirmReceivedForAdmin(Long id) {
        AfterSale afterSale = requireExists(id);
        AfterSaleView result = confirmReceived(afterSale);
        notifyAfterSaleResult(afterSale, "平台已确认收到退货，退款完成");
        return result;
    }

    @Override
    public AfterSaleStatsView statsForAdmin() {
        return stats(afterSaleMapper.countAll());
    }

    @Override
    public List<AfterSaleReasonView> adminReasons() {
        return reasonMapper.selectList(Wrappers.<AfterSaleReason>lambdaQuery()
                        .orderByAsc(AfterSaleReason::getSort)
                        .orderByAsc(AfterSaleReason::getId))
                .stream().map(this::reasonView).toList();
    }

    @Override
    @Transactional
    public AfterSaleReasonView createReason(AfterSaleReasonRequest request) {
        Long exists = reasonMapper.selectCount(Wrappers.<AfterSaleReason>lambdaQuery()
                .eq(AfterSaleReason::getCode, request.code()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "原因编码已存在");
        }
        AfterSaleReason reason = new AfterSaleReason();
        reason.setCode(request.code());
        reason.setLabel(request.label());
        reason.setSort(request.sort() == null ? 0 : request.sort());
        reason.setStatus(request.status() == null ? 1 : request.status());
        reasonMapper.insert(reason);
        return reasonView(reason);
    }

    @Override
    @Transactional
    public AfterSaleReasonView updateReason(Long id, AfterSaleReasonRequest request) {
        AfterSaleReason reason = reasonMapper.selectById(id);
        if (reason == null) {
            throw new BizException(ResultCode.NOT_FOUND, "售后原因不存在");
        }
        reason.setCode(request.code());
        reason.setLabel(request.label());
        if (request.sort() != null) {
            reason.setSort(request.sort());
        }
        if (request.status() != null) {
            reason.setStatus(request.status());
        }
        reasonMapper.updateById(reason);
        return reasonView(reason);
    }

    /* ==================== 超时扫描 ==================== */

    @Override
    public int autoApproveExpired(int reviewHours) {
        List<AfterSale> expired = afterSaleMapper.selectReviewExpired(50);
        int done = 0;
        for (AfterSale afterSale : expired) {
            try {
                Boolean ok = transactionTemplate.execute(s -> {
                    approve(afterSale, "商家超时未处理，系统自动同意");
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    done++;
                    log.info("售后超时自动同意: afterSaleNo={}", afterSale.getAfterSaleNo());
                    notifyAfterSaleResult(afterSale, "商家超时未处理，系统自动同意");
                }
            } catch (RuntimeException e) {
                log.warn("售后超时自动同意失败: afterSaleNo={} err={}",
                        afterSale.getAfterSaleNo(), e.getMessage());
            }
        }
        return done;
    }

    @Override
    public int autoConfirmReturnExpired(int returnConfirmDays) {
        List<AfterSale> expired = afterSaleMapper.selectReturnConfirmExpired(returnConfirmDays, 50);
        int done = 0;
        for (AfterSale afterSale : expired) {
            try {
                Boolean ok = transactionTemplate.execute(s -> {
                    confirmReceived(requireExists(afterSale.getId()));
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    done++;
                    log.info("退货超时自动确认收货: afterSaleNo={}", afterSale.getAfterSaleNo());
                    notifyAfterSaleResult(afterSale, "退货超时未确认，系统自动确认收货并退款");
                }
            } catch (RuntimeException e) {
                log.warn("退货超时自动确认失败: afterSaleNo={} err={}",
                        afterSale.getAfterSaleNo(), e.getMessage());
            }
        }
        return done;
    }

    /* ==================== 内部：审核与执行 ==================== */

    /** 售后处理结果通知商家（平台处理 / 自动超时处理时调用）；失败不影响主流程 */
    private void notifyAfterSaleResult(AfterSale afterSale, String note) {
        if (afterSale == null || afterSale.getMerchantId() == null) {
            return;
        }
        try {
            notificationService.notifyMerchant(afterSale.getMerchantId(), "AFTER_SALE_RESULT", "售后处理结果",
                    String.format("售后单 %s：%s。", afterSale.getAfterSaleNo(), note),
                    "/merchant/after-sales?afterSaleId=" + afterSale.getId(),
                    "/admin/after-sales?afterSaleId=" + afterSale.getId(),
                    "after-sale-result:" + afterSale.getAfterSaleNo());
        } catch (RuntimeException e) {
            log.warn("售后 {} 写通知失败: {}", afterSale.getAfterSaleNo(), e.getMessage());
        }
    }

    /** 同意：仅退款直接执行退款；退货退款进入「待买家退货」。autoNote 用于系统自动处理的说明。 */
    private AfterSaleView approve(AfterSale afterSale, String autoNote) {
        if (!ST_PENDING_REVIEW.equals(afterSale.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该售后单已处理，请刷新");
        }
        Order order = orderMapper.selectByIdForUpdate(afterSale.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        List<AfterSaleItem> items = itemsOf(afterSale.getId());
        Plan plan = planByStoredItems(order, items);

        if (TYPE_REFUND_ONLY.equals(afterSale.getType())) {
            cas(afterSale, ST_PENDING_REVIEW, ST_REFUNDED);
            afterSale.setStatus(ST_REFUNDED);
            afterSale.setReviewedAt(LocalDateTime.now());
            afterSale.setRefundedAt(LocalDateTime.now());
            afterSale.setRefundAmount(plan.total());
            executeRefund(afterSale, order, plan);
        } else {
            cas(afterSale, ST_PENDING_REVIEW, ST_WAITING_RETURN);
            afterSale.setStatus(ST_WAITING_RETURN);
            afterSale.setReviewedAt(LocalDateTime.now());
        }
        afterSaleMapper.updateById(afterSale);
        return view(afterSale, items, null);
    }

    /** 驳回：必须写理由；订单状态不变。 */
    private AfterSaleView reject(AfterSale afterSale, String reason) {
        cas(afterSale, ST_PENDING_REVIEW, ST_REJECTED);
        afterSale.setStatus(ST_REJECTED);
        afterSale.setRejectReason(reason.trim());
        afterSale.setReviewedAt(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        return view(afterSale, itemsOf(afterSale.getId()), null);
    }

    /** 商家确认收货：退货退款马上执行退款。 */
    private AfterSaleView confirmReceived(AfterSale afterSale) {
        if (!ST_RETURN_SHIPPED.equals(afterSale.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该售后单还没有退货物流信息");
        }
        Order order = orderMapper.selectByIdForUpdate(afterSale.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        Plan plan = planByStoredItems(order, itemsOf(afterSale.getId()));
        cas(afterSale, ST_RETURN_SHIPPED, ST_REFUNDED);
        afterSale.setStatus(ST_REFUNDED);
        afterSale.setReceivedAt(LocalDateTime.now());
        afterSale.setRefundedAt(LocalDateTime.now());
        afterSale.setRefundAmount(plan.total());
        executeRefund(afterSale, order, plan);
        afterSaleMapper.updateById(afterSale);
        return view(afterSale, itemsOf(afterSale.getId()), null);
    }

    /**
     * 执行退款：库存回补（按项）+ 订单累计已退金额 + 整单退完时改订单状态 / 支付流水 / 退券。
     * 必须在外层事务内调用；CAS 与补偿任一步失败都会整体回滚。
     */
    private void executeRefund(AfterSale afterSale, Order order, Plan plan) {
        for (AfterSaleItem item : plan.items()) {
            skuMapper.restoreStock(item.getSkuId(), item.getQuantity());
            stockService.release(item.getSkuId(), item.getQuantity());
            productMapper.applyRestored(item.getProductId(), item.getQuantity());
        }
        orderMapper.addRefundedAmount(order.getId(), plan.total());

        BigDecimal before = nz(order.getRefundedAmount());
        BigDecimal newRefunded = before.add(plan.total());
        boolean fullRefund = newRefunded.compareTo(nz(order.getTotalAmount())) >= 0;
        LocalDateTime now = LocalDateTime.now();

        if (fullRefund) {
            if (orderMapper.compareAndSetStatus(order.getId(), order.getStatus(), "REFUNDED") == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "订单状态已变化，请刷新后重试");
            }
            orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                    .eq(Order::getId, order.getId())
                    .set(Order::getRefundedAmount, newRefunded)
                    .set(Order::getRefundReason, reasonLabel(afterSale.getReasonCode()))
                    .set(Order::getRefundAppliedAt, afterSale.getCreatedAt())
                    .set(Order::getRefundHandledAt, now)
                    .set(Order::getStatusBeforeRefund, null));
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getOrderId, order.getId())
                    .eq(Payment::getStatus, Payment.STATUS_SUCCESS)
                    .set(Payment::getStatus, Payment.STATUS_REFUNDED)
                    .set(Payment::getRefundedAt, now));
            // 券只在整单退完时退回；失败不阻断退款（账务小问题，卡住退款是大问题）
            try {
                userCouponMapper.restoreByOrderId(order.getId());
            } catch (RuntimeException e) {
                log.warn("售后退券失败: orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
        }
    }

    /* ==================== 内部：金额与校验 ==================== */

    /** 申请时的金额估算（含「本次退完整单」的尾差补齐）。 */
    private Plan plan(Order order, List<OrderItem> orderItems, List<AfterSaleItemRequest> requests) {
        Map<Long, OrderItem> byId = new LinkedHashMap<>();
        for (OrderItem item : orderItems) {
            byId.put(item.getId(), item);
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<Line> lines = new ArrayList<>();
        for (AfterSaleItemRequest req : requests) {
            OrderItem orderItem = byId.get(req.orderItemId());
            if (orderItem == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "订单里没有这个商品");
            }
            if (!seen.add(req.orderItemId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "同一个商品只能提交一次");
            }
            int occupied = afterSaleItemMapper.occupiedQuantity(orderItem.getId());
            if (occupied + req.quantity() > orderItem.getQuantity()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "「" + orderItem.getProductName() + "」最多还能退 "
                                + (orderItem.getQuantity() - occupied) + " 件");
            }
            lines.add(new Line(orderItem, req.quantity()));
        }

        BigDecimal ratio = refundRatio(order);
        BigDecimal total = ZERO;
        List<AfterSaleItem> items = new ArrayList<>();
        for (Line line : lines) {
            BigDecimal refund = line.price().multiply(BigDecimal.valueOf(line.quantity()))
                    .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            total = total.add(refund);
            items.add(toAfterSaleItem(line, refund));
        }
        boolean full = isFullRefund(orderItems, requests);
        if (full) {
            BigDecimal target = nz(order.getTotalAmount()).subtract(nz(order.getRefundedAmount()));
            if (target.signum() < 0) {
                target = ZERO;
            }
            BigDecimal diff = target.subtract(total);
            if (!items.isEmpty()) {
                AfterSaleItem last = items.get(items.size() - 1);
                last.setRefundAmount(last.getRefundAmount().add(diff));
            }
            total = target;
        }
        return new Plan(items, total, full);
    }

    /** 执行退款时按库里已存的售后项重新算（订单累计已退可能已被别的售后单改过）。 */
    private Plan planByStoredItems(Order order, List<AfterSaleItem> storedItems) {
        BigDecimal ratio = refundRatio(order);
        BigDecimal total = ZERO;
        List<AfterSaleItem> items = new ArrayList<>();
        for (AfterSaleItem stored : storedItems) {
            AfterSaleItem item = new AfterSaleItem();
            item.setOrderItemId(stored.getOrderItemId());
            item.setProductId(stored.getProductId());
            item.setSkuId(stored.getSkuId());
            item.setProductName(stored.getProductName());
            item.setProductImage(stored.getProductImage());
            item.setSpecText(stored.getSpecText());
            item.setUnitPrice(stored.getUnitPrice());
            item.setQuantity(stored.getQuantity());
            BigDecimal refund = stored.getUnitPrice().multiply(BigDecimal.valueOf(stored.getQuantity()))
                    .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            item.setRefundAmount(refund);
            total = total.add(refund);
            items.add(item);
        }
        // 本次是否把整单退完：按「订单项累计已退数量」判断
        BigDecimal target = nz(order.getTotalAmount()).subtract(nz(order.getRefundedAmount()));
        if (target.signum() < 0) {
            target = ZERO;
        }
        if (willCompleteOrder(order, storedItems)) {
            BigDecimal diff = target.subtract(total);
            if (!items.isEmpty()) {
                AfterSaleItem last = items.get(items.size() - 1);
                last.setRefundAmount(last.getRefundAmount().add(diff));
            }
            total = target;
            return new Plan(items, total, true);
        }
        return new Plan(items, total, false);
    }

    /** 判断「本次售后加上历史已退，是否把订单所有商品都退完」。 */
    private boolean isFullRefund(List<OrderItem> orderItems, List<AfterSaleItemRequest> requests) {
        Map<Long, Integer> planQty = new LinkedHashMap<>();
        for (AfterSaleItemRequest req : requests) {
            planQty.merge(req.orderItemId(), req.quantity(), Integer::sum);
        }
        for (OrderItem item : orderItems) {
            int occupied = afterSaleItemMapper.occupiedQuantity(item.getId());
            int planned = planQty.getOrDefault(item.getId(), 0);
            if (occupied + planned < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行时判断「是否把订单所有商品都退完」。
     * 调用前当前售后单状态已 CAS 成 REFUNDED，所以 {@code occupiedQuantity} 已经把本次算进去了。
     */
    private boolean willCompleteOrder(Order order, List<AfterSaleItem> storedItems) {
        List<OrderItem> orderItems = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : orderItems) {
            if (afterSaleItemMapper.occupiedQuantity(item.getId()) < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    /** 优惠按订单维度的比例分摊：比例 = 实付 / 原价。 */
    private BigDecimal refundRatio(Order order) {
        BigDecimal total = nz(order.getTotalAmount());
        BigDecimal original = nz(order.getOriginalAmount());
        if (original.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return total.divide(original, 8, RoundingMode.HALF_UP);
    }

    private AfterSaleItem toAfterSaleItem(Line line, BigDecimal refund) {
        AfterSaleItem item = new AfterSaleItem();
        item.setOrderItemId(line.item().getId());
        item.setProductId(line.item().getProductId());
        item.setSkuId(line.item().getSkuId());
        item.setProductName(line.item().getProductName());
        item.setProductImage(nullToEmpty(line.item().getProductImage()));
        item.setSpecText(nullToEmpty(line.item().getSpecText()));
        item.setUnitPrice(line.item().getUnitPrice());
        item.setQuantity(line.quantity());
        item.setRefundAmount(refund);
        return item;
    }

    private record Line(OrderItem item, int quantity) {
        BigDecimal price() {
            return item.getUnitPrice() == null ? ZERO : item.getUnitPrice();
        }
    }

    private record Plan(List<AfterSaleItem> items, BigDecimal total, boolean fullRefund) {
    }

    /* ==================== 内部：查询与视图 ==================== */

    private PageResponse<AfterSaleView> page(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AfterSale> wrapper,
            int page, int size) {
        Page<AfterSale> result = afterSaleMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                wrapper.orderByDesc(AfterSale::getId));
        List<AfterSaleView> records = new ArrayList<>();
        for (AfterSale afterSale : result.getRecords()) {
            records.add(view(afterSale, null, null));
        }
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private AfterSaleView view(AfterSale afterSale, List<AfterSaleItem> items, String ignored) {
        List<AfterSaleItem> detailItems = items != null ? items : itemsOf(afterSale.getId());
        Merchant merchant = merchantMapper.selectById(afterSale.getMerchantId());
        String merchantName = merchant == null ? "" : nullToEmpty(merchant.getName());
        String reasonLabel = reasonLabel(afterSale.getReasonCode());
        String status = afterSale.getStatus();
        return new AfterSaleView(
                afterSale.getId(),
                afterSale.getAfterSaleNo(),
                afterSale.getOrderId(),
                afterSale.getOrderNo(),
                afterSale.getUserId(),
                afterSale.getMerchantId(),
                merchantName,
                afterSale.getType(),
                TYPE_LABELS.getOrDefault(afterSale.getType(), afterSale.getType()),
                status,
                STATUS_LABELS.getOrDefault(status, status),
                afterSale.getReasonCode(),
                reasonLabel,
                afterSale.getReasonText(),
                readImages(afterSale.getEvidenceImages()),
                afterSale.getApplyAmount(),
                afterSale.getRefundAmount(),
                afterSale.getRejectReason(),
                afterSale.getReturnTrackingNo(),
                afterSale.getReturnCarrier(),
                afterSale.getReviewDeadline(),
                afterSale.getReviewedAt(),
                afterSale.getShippedBackAt(),
                afterSale.getReceivedAt(),
                afterSale.getRefundedAt(),
                afterSale.getCancelledAt(),
                afterSale.getCreatedAt(),
                detailItems,
                timeline(afterSale),
                ST_PENDING_REVIEW.equals(status),
                ST_PENDING_REVIEW.equals(status),
                ST_RETURN_SHIPPED.equals(status),
                ST_PENDING_REVIEW.equals(status) || ST_WAITING_RETURN.equals(status),
                ST_WAITING_RETURN.equals(status));
    }

    private List<AfterSaleView.TimelineItem> timeline(AfterSale afterSale) {
        List<AfterSaleView.TimelineItem> lines = new ArrayList<>();
        lines.add(new AfterSaleView.TimelineItem(afterSale.getCreatedAt(), "提交售后申请",
                TYPE_LABELS.getOrDefault(afterSale.getType(), afterSale.getType())
                        + " · " + reasonLabel(afterSale.getReasonCode())));
        if (afterSale.getReviewedAt() != null) {
            if (ST_REJECTED.equals(afterSale.getStatus())) {
                lines.add(new AfterSaleView.TimelineItem(afterSale.getReviewedAt(), "商家驳回",
                        nullToEmpty(afterSale.getRejectReason())));
            } else {
                lines.add(new AfterSaleView.TimelineItem(afterSale.getReviewedAt(), "商家同意售后",
                        TYPE_RETURN_REFUND.equals(afterSale.getType()) ? "等待买家寄回商品" : "退款已发起"));
            }
        }
        if (afterSale.getShippedBackAt() != null) {
            lines.add(new AfterSaleView.TimelineItem(afterSale.getShippedBackAt(), "买家已寄回",
                    (nullToEmpty(afterSale.getReturnCarrier()) + " " + nullToEmpty(afterSale.getReturnTrackingNo())).trim()));
        }
        if (afterSale.getReceivedAt() != null) {
            lines.add(new AfterSaleView.TimelineItem(afterSale.getReceivedAt(), "商家确认收货", ""));
        }
        if (afterSale.getRefundedAt() != null) {
            lines.add(new AfterSaleView.TimelineItem(afterSale.getRefundedAt(), "退款完成",
                    "¥" + nz(afterSale.getRefundAmount()).toPlainString()));
        }
        if (afterSale.getCancelledAt() != null) {
            lines.add(new AfterSaleView.TimelineItem(afterSale.getCancelledAt(), "买家撤销申请", ""));
        }
        return lines;
    }

    private List<AfterSaleItem> itemsOf(Long afterSaleId) {
        return afterSaleItemMapper.selectList(Wrappers.<AfterSaleItem>lambdaQuery()
                .eq(AfterSaleItem::getAfterSaleId, afterSaleId)
                .orderByAsc(AfterSaleItem::getId));
    }

    private AfterSale requireExists(Long id) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw new BizException(ResultCode.NOT_FOUND, "售后单不存在");
        }
        return afterSale;
    }

    private AfterSale requireOwnedByMerchant(Long merchantId, Long id) {
        AfterSale afterSale = requireExists(id);
        if (!merchantId.equals(afterSale.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "售后单不存在");
        }
        return afterSale;
    }

    private void cas(AfterSale afterSale, String from, String to) {
        if (afterSaleMapper.compareAndSetStatus(afterSale.getId(), from, to) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "售后单状态已变化，请刷新后重试");
        }
    }

    private AfterSaleStatsView stats(List<Map<String, Object>> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : ALL_STATUSES) {
            counts.put(status, 0L);
        }
        long total = 0;
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long cnt = ((Number) row.get("cnt")).longValue();
            counts.put(status, cnt);
            total += cnt;
        }
        return new AfterSaleStatsView(counts, total);
    }

    private AfterSaleReasonView reasonView(AfterSaleReason reason) {
        return new AfterSaleReasonView(reason.getId(), reason.getCode(), reason.getLabel(),
                reason.getSort(), reason.getStatus());
    }

    private String reasonLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        AfterSaleReason reason = reasonMapper.selectOne(Wrappers.<AfterSaleReason>lambdaQuery()
                .eq(AfterSaleReason::getCode, code));
        return reason == null ? code : reason.getLabel();
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (RuntimeException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证图片格式不正确");
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private String normalizeType(String type) {
        if (TYPE_REFUND_ONLY.equals(type) || TYPE_RETURN_REFUND.equals(type)) {
            return type;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "售后类型不正确");
    }

    private boolean isRefundableStatus(String status) {
        return "PAID".equals(status) || "SHIPPED".equals(status) || "COMPLETED".equals(status);
    }

    private String generateNo() {
        return "AS" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    /** 订单状态字符串常量（避免与 entity 的枚举耦合出循环依赖）。 */
    private static final class OrderStatusValue {
        private static final String SHIPPED = "SHIPPED";
        private static final String COMPLETED = "COMPLETED";
    }
}
