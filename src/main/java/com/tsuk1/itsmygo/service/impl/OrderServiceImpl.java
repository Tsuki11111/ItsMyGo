package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.UserCoupon;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.OrderService;
import com.tsuk1.itsmygo.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private static final int ON_SALE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 超时未支付自动取消的默认时长（分钟） */
    private static final int DEFAULT_CANCEL_EXPIRED_MINUTES = 30;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final AddressMapper addressMapper;
    private final PaymentMapper paymentMapper;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;
    private final ReviewMapper reviewMapper;
    private final UserCouponMapper userCouponMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            CartItemMapper cartItemMapper, ProductMapper productMapper,
                            ProductSkuMapper skuMapper, AddressMapper addressMapper,
                            PaymentMapper paymentMapper, StockService stockService,
                            StringRedisTemplate redisTemplate, ReviewMapper reviewMapper,
                            UserCouponMapper userCouponMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.addressMapper = addressMapper;
        this.paymentMapper = paymentMapper;
        this.stockService = stockService;
        this.redisTemplate = redisTemplate;
        this.reviewMapper = reviewMapper;
        this.userCouponMapper = userCouponMapper;
    }

    /**
     * 提交令牌存在 Redis 里，key 里带 userId —— 别人的令牌用不了。
     * TTL 是为了不用的令牌自然消失（也满足共用 Redis「key 必须带 TTL」的约束）。
     */
    private static final String TOKEN_PREFIX = "its-my-go:order-token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    @Override
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tokenKey(userId, token), "1", TOKEN_TTL);
        return token;
    }

    /**
     * 消费令牌。**DEL 本身是原子的**，两个并发请求同时删同一个 key 只有一个返回 1，
     * 所以不需要额外加锁就能保证「只有一个请求能往下走」。
     */
    private void consumeToken(Long userId, String token) {
        if (!Boolean.TRUE.equals(redisTemplate.delete(tokenKey(userId, token)))) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单已提交或令牌已失效，请勿重复提交");
        }
    }

    /** 业务失败时把令牌还回去，让用户可以修正后重试（比如库存不足改数量） */
    private void restoreToken(Long userId, String token) {
        redisTemplate.opsForValue().set(tokenKey(userId, token), "1", TOKEN_TTL);
    }

    private String tokenKey(Long userId, String token) {
        return TOKEN_PREFIX + userId + ":" + token;
    }

    @Override
    @Transactional
    public OrderView create(Long userId, CreateOrderRequest request) {
        // 令牌校验放在最前面：重复提交在这里就被挡住，一个字节的业务逻辑都不会执行
        consumeToken(userId, request.token());
        try {
            return doCreate(userId, request);
        } catch (RuntimeException e) {
            // 注意：这是「业务失败」，不是「重复提交」——令牌要还回去
            restoreToken(userId, request.token());
            throw e;
        }
    }

    private OrderView doCreate(Long userId, CreateOrderRequest request) {
        Address address = addressMapper.selectById(request.addressId());
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "收货地址不存在");
        }

        // 商品有两种来源，之后一视同仁：立即购买（直接给定）or 购物车勾选项
        boolean fromCart = request.items() == null || request.items().isEmpty();
        List<OrderLine> lines = fromCart ? selectedCartLines(userId) : buyNowLines(request.items());

        Map<Long, Product> products = productMapper.selectByIds(
                        lines.stream().map(OrderLine::productId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSku> skus = skuMapper.selectByIds(
                        lines.stream().map(OrderLine::skuId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        // 已成功扣减的 **SKU** → 数量；出错时按它回补 Redis
        // （数据库那边由 @Transactional 自动回滚，Redis 不在事务里，必须手动补）
        Map<Long, Integer> reserved = new LinkedHashMap<>();

        try {
            List<OrderItem> items = new ArrayList<>(lines.size());
            BigDecimal total = BigDecimal.ZERO;

            for (OrderLine line : lines) {
                Product product = products.get(line.productId());
                if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            fromCart ? "商品已下架，请先移出购物车" : "商品已下架");
                }
                ProductSku sku = skus.get(line.skuId());
                if (sku == null || !line.productId().equals(sku.getProductId())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "商品规格不存在，请重新选择");
                }
                if (!Integer.valueOf(ON_SALE).equals(sku.getStatus())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "该规格已停售");
                }

                reserveStock(sku, line.quantity(), reserved);

                OrderItem item = new OrderItem();
                item.setProductId(product.getId());
                item.setSkuId(sku.getId());
                // 以下都是快照：商品改价改名、规格改名甚至被删，都不影响这张订单
                item.setProductName(product.getName());
                item.setProductImage(StringUtils.hasText(sku.getImage())
                        ? sku.getImage() : product.getMainImage());
                item.setSpecText(sku.getSpecText());
                item.setUnitPrice(sku.getPrice());
                item.setQuantity(line.quantity());
                item.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
                items.add(item);

                total = total.add(item.getSubtotal());
            }

            // 优惠券：先校验并算出实付，最终能否使用以插单后的条件更新为准
            UserCoupon userCoupon = null;
            BigDecimal discount = BigDecimal.ZERO;
            if (request.couponId() != null) {
                userCoupon = userCouponMapper.selectById(request.couponId());
                if (userCoupon == null || !userId.equals(userCoupon.getUserId())) {
                    // 别人的券一律当不存在
                    throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
                }
                if (!UserCoupon.UNUSED.equals(userCoupon.getStatus())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "优惠券已使用");
                }
                if (userCoupon.getThresholdAmount().compareTo(total) > 0) {
                    throw new BizException(ResultCode.BAD_REQUEST, "订单金额未达到优惠券使用门槛");
                }
                discount = userCoupon.getDiscountAmount().min(total);
            }

            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setOriginalAmount(total);
            order.setDiscountAmount(discount);
            order.setTotalAmount(total.subtract(discount));
            order.setUserCouponId(userCoupon == null ? null : userCoupon.getId());
            order.setCouponName(userCoupon == null ? "" : userCoupon.getCouponName());
            order.setStatus(OrderStatus.PENDING_PAYMENT.name());
            // 收货信息也快照：用户以后改地址或删地址，这张订单不受影响
            order.setReceiver(address.getReceiver());
            order.setPhone(address.getPhone());
            order.setAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
            order.setTrackingNo("");
            orderMapper.insert(order);

            // 券的使用是条件更新：期间被别的订单用掉/过期就 0 行，整单回滚
            if (userCoupon != null
                    && userCouponMapper.markUsed(userCoupon.getId(), userId, order.getId()) == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "优惠券已使用或已过期");
            }

            for (OrderItem item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 只有购物车结算才清空勾选行 —— 「立即购买」从头到尾没碰过购物车
            if (fromCart) {
                cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, 1));
            }

            return new OrderView(order, OrderStatus.PENDING_PAYMENT.label(), items, items.size());

        } catch (RuntimeException e) {
            reserved.forEach(stockService::release);
            throw e;
        }
    }

    /** 下单的商品行 —— 购物车来的和「立即购买」来的，后面走同一套流程 */
    private record OrderLine(Long productId, Long skuId, int quantity) {
    }

    private List<OrderLine> selectedCartLines(Long userId) {
        List<CartItem> cartItems = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));
        if (cartItems.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先勾选要购买的商品");
        }
        return cartItems.stream()
                .map(cartItem -> new OrderLine(cartItem.getProductId(), cartItem.getSkuId(),
                        cartItem.getQuantity()))
                .toList();
    }

    private List<OrderLine> buyNowLines(List<CreateOrderRequest.BuyNowItem> items) {
        return items.stream()
                .map(item -> new OrderLine(item.productId(), item.skuId(), item.quantity()))
                .toList();
    }

    /**
     * 两层防线（粒度是 **SKU**）：
     *   1. Redis 预扣 —— 原子 Lua，把大部分并发挡在数据库外
     *   2. 数据库条件更新 —— {@code WHERE stock >= ?}，防超卖的最终真相
     * 第一层过了第二层没过（例如 Redis 数据偏乐观），照样按库存不足处理。
     */
    private void reserveStock(ProductSku sku, int quantity, Map<Long, Integer> reserved) {
        if (!stockService.tryReserve(sku.getId(), quantity)) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }
        reserved.merge(sku.getId(), quantity, Integer::sum);

        if (skuMapper.deductStock(sku.getId(), quantity) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }
        // 商品上的库存/销量是各 SKU 的汇总，只服务列表页显示，**不参与任何判定**
        productMapper.applyDeducted(sku.getProductId(), quantity);
    }

    /**
     * 顾客端订单列表 —— **两条查询 + 内存组装**，不是 N+1、也不是 JOIN。
     *
     * ① 按条件分页查订单（分页只在这一步做，符合直觉）
     * ② 用 IN 把这一页的明细一次捞回来
     * ③ 按 orderId 分组，内存里拼成 OrderView
     *
     * **为什么不用 JOIN**：{@code LEFT JOIN order_items ... LIMIT 10} 里的 LIMIT
     * 限制的是 JOIN 之后的行数而不是订单数 —— 10 个订单若有 30 件商品，
     * 只能拿到前 3 个订单，分页直接错。要修就得套子查询或聚合，SQL 会难读到没人愿意维护。
     *
     * 真实电商（淘宝/京东那类）也是这个路子；流量再大才会考虑在 orders 上冗余商品摘要，
     * 让列表连第二条查询都省掉。
     */
    @Override
    public PageResponse<OrderView> pageForCustomer(Long userId, int page, int size,
                                                   int previewLimit, String status) {
        List<String> statuses = parseStatuses(status);
        Page<Order> orderPage = orderMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        // 空集合不能直接传给 in —— 会拼出非法的 IN ()
                        .in(!statuses.isEmpty(), Order::getStatus, statuses)
                        .orderByDesc(Order::getId));

        List<Order> orders = orderPage.getRecords();
        if (orders.isEmpty()) {
            return new PageResponse<>(List.of(), orderPage.getTotal(),
                    orderPage.getCurrent(), orderPage.getSize());
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderItemMapper.selectList(
                        Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 已评价标记：只对已完成订单回填，整页一次查询（不能按订单 N+1）
        List<OrderItem> completedItems = orders.stream()
                .filter(order -> OrderStatus.COMPLETED.name().equals(order.getStatus()))
                .flatMap(order -> itemsByOrder.getOrDefault(order.getId(), List.of()).stream())
                .toList();
        fillReviewed(completedItems);

        List<OrderView> views = new ArrayList<>(orders.size());
        for (Order order : orders) {
            List<OrderItem> all = itemsByOrder.getOrDefault(order.getId(), List.of());
            List<OrderItem> shown = (previewLimit > 0 && all.size() > previewLimit)
                    ? all.subList(0, previewLimit)
                    : all;
            views.add(new OrderView(order,
                    OrderStatus.valueOf(order.getStatus()).label(), shown, all.size()));
        }

        return new PageResponse<>(views, orderPage.getTotal(),
                orderPage.getCurrent(), orderPage.getSize());
    }

    @Override
    public OrderView detailForCustomer(Long userId, Long orderId) {
        return view(requireOwned(userId, orderId));
    }

    @Override
    @Transactional
    public OrderView pay(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        transition(order, OrderStatus.PAID, order::setPaidAt);

        // 落支付流水。uk_order_id 是数据库层兜底：即使有并发请求绕过了状态机，
        // 第二条也会撞唯一索引，绝不会出现「一个订单两条成功支付记录」。
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo("PAY"));
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod("MOCK");
        payment.setStatus(Payment.STATUS_SUCCESS);
        payment.setPaidAt(order.getPaidAt());
        paymentMapper.insert(payment);

        return view(order);
    }

    // ── 售后：退款 ──

    @Override
    @Transactional
    public OrderView applyRefund(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.refundable()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单当前是「" + current.label() + "」，不能申请退款");
        }

        // 先记下申请前的状态 —— 管理员驳回时要回退到它。
        // 必须在 transition 之前设置：transition 内部的 updateById 会一并写进去。
        order.setStatusBeforeRefund(current.name());
        transition(order, OrderStatus.REFUNDING, t -> { });
        return view(order);
    }

    @Override
    @Transactional
    public OrderView approveRefund(Long orderId) {
        Order order = requireExists(orderId);
        if (OrderStatus.valueOf(order.getStatus()) != OrderStatus.REFUNDING) {
            throw new BizException(ResultCode.BAD_REQUEST, "该订单没有待处理的退款申请");
        }

        transition(order, OrderStatus.REFUNDED, t -> { });

        // 钱退回去了，货也要退回来
        restoreStock(order);
        restoreCoupon(order);

        // 支付流水同步置为已退款（这是退款真正的凭据）
        Payment payment = paymentMapper.selectOne(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, orderId));
        if (payment != null) {
            payment.setStatus(Payment.STATUS_REFUNDED);
            payment.setRefundedAt(LocalDateTime.now());
            paymentMapper.updateById(payment);
        }

        order.setStatusBeforeRefund(null);
        orderMapper.updateById(order);
        return view(order);
    }

    @Override
    @Transactional
    public OrderView rejectRefund(Long orderId) {
        Order order = requireExists(orderId);
        if (OrderStatus.valueOf(order.getStatus()) != OrderStatus.REFUNDING) {
            throw new BizException(ResultCode.BAD_REQUEST, "该订单没有待处理的退款申请");
        }
        String before = order.getStatusBeforeRefund();
        if (before == null) {
            // 理论上不会发生；真发生了说明数据坏了，别猜，直接拒绝
            throw new BizException(ResultCode.INTERNAL_ERROR, "订单缺少退款前的状态，无法驳回");
        }

        transition(order, OrderStatus.valueOf(before), t -> { });
        order.setStatusBeforeRefund(null);
        orderMapper.updateById(order);
        return view(order);
    }

    /**
     * 取消超时未支付的订单并回滚库存。
     * 先做成管理端手动触发；将来要自动化，挂个定时任务调这个方法即可
     * （注意多实例下要加分布式锁，否则会重复执行 —— 虽然幂等，但白跑）。
     */
    @Override
    @Transactional
    public int cancelExpired(int minutes) {
        int window = minutes > 0 ? minutes : DEFAULT_CANCEL_EXPIRED_MINUTES;

        // ⚠️ 时间基准必须用数据库的 NOW()，不能拿 JVM 的 LocalDateTime.now() 去比。
        // 本机 MySQL 容器跑在 UTC、应用跑在 UTC+8，两者差 8 小时 ——
        // 用应用时间去比的话，刚下单的订单会被当成「已经超时 8 小时」立刻取消掉。
        // （这个 bug 是测试 freshOrderIsNotCancelledByExpiryJob 抓出来的。）
        List<Order> expired = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .apply("created_at < DATE_SUB(NOW(), INTERVAL {0} MINUTE)", window));

        int cancelled = 0;
        for (Order order : expired) {
            try {
                transition(order, OrderStatus.CANCELLED, order::setCancelledAt);
                restoreStock(order);
                restoreCoupon(order);
                cancelled++;
            } catch (RuntimeException e) {
                // 单个失败不影响其他的（典型情况：刚好在这一刻被人支付了）。
                // 这里 catch 住了不会标记 rollback-only —— transition 是同类内部调用、
                // 不走事务代理，异常没有穿过任何 @Transactional 边界。
                log.warn("取消超时订单失败: orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
        }
        if (cancelled > 0) {
            log.info("已取消 {} 笔超时未支付订单（阈值 {} 分钟）", cancelled, window);
        }
        return cancelled;
    }

    @Override
    @Transactional
    public OrderView cancel(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.cancellable()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单当前是「" + current.label() + "」，不能取消");
        }
        transition(order, OrderStatus.CANCELLED, order::setCancelledAt);
        restoreStock(order);
        restoreCoupon(order);
        return view(order);
    }

    @Override
    @Transactional
    public OrderView confirmReceipt(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        transition(order, OrderStatus.COMPLETED, order::setCompletedAt);
        return view(order);
    }

    @Override
    public PageResponse<Order> pageForAdmin(int page, int size, String status) {
        Page<Order> pageRequest = new Page<>(normalizePage(page), normalizeSize(size));
        return PageResponse.of(orderMapper.selectPage(pageRequest, Wrappers.<Order>lambdaQuery()
                .eq(StringUtils.hasText(status), Order::getStatus, status)
                .orderByDesc(Order::getId)));
    }

    @Override
    public OrderView detailForAdmin(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return view(order);
    }

    @Override
    @Transactional
    public OrderView ship(Long orderId, ShipRequest request) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        transition(order, OrderStatus.SHIPPED, order::setShippedAt);
        order.setTrackingNo(request.trackingNo());
        orderMapper.updateById(order);
        return view(order);
    }

    /** 取消时把库存和销量都退回去 —— SKU 行、Redis 计数器、商品汇总三处都要动 */
    private void restoreStock(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            skuMapper.restoreStock(item.getSkuId(), item.getQuantity());
            stockService.release(item.getSkuId(), item.getQuantity());
            productMapper.applyRestored(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 取消/退款时退券：未过期恢复 UNUSED，过期置 EXPIRED。
     * 条件更新本身幂等；**退券失败只记日志，不阻断订单状态回滚** ——
     * 券没退成是账务小问题，订单卡在退款中是大问题。
     */
    private void restoreCoupon(Order order) {
        try {
            userCouponMapper.restoreByOrderId(order.getId());
        } catch (RuntimeException e) {
            log.warn("退还优惠券失败: orderNo={} err={}", order.getOrderNo(), e.getMessage());
        }
    }

    /**
     * 状态流转的唯一入口：先查状态机允许不允许，再改状态和时间戳。
     * 非法流转在这里被挡住（例如「已取消」的订单不能被发货）。
     *
     * ⚠️ **关键在最后那步用的是条件更新而不是 updateById**：
     * 如果写成「transition 里 updateById」，那么「读出来判断」和「写回去」之间是有窗口的 ——
     * 两个并发的支付请求会双双读到 PENDING_PAYMENT、双双通过 canTransitionTo 检查，
     * 结果一个订单产生两条支付流水。改成 {@code WHERE status = 期望值} 之后，
     * 第二个请求影响行数为 0，直接被拒。
     *
     * 这跟库存扣减用的是同一个套路（§3.8）：**把判断和修改合成一条 SQL**。
     */
    private void transition(Order order, OrderStatus target, Consumer<LocalDateTime> stampSetter) {
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单当前是「" + current.label() + "」，不能变成「" + target.label() + "」");
        }

        if (orderMapper.compareAndSetStatus(order.getId(), current.name(), target.name()) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单状态已变化，请刷新后重试");
        }

        // 状态已经被上面那条 SQL 改掉了，这里只是把时间戳补上
        order.setStatus(target.name());
        stampSetter.accept(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private String generateOrderNo() {
        return generateNo("");
    }

    private String generatePaymentNo(String prefix) {
        return generateNo(prefix);
    }

    private String generateNo(String prefix) {
        return prefix + ORDER_NO_TIME.format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** 管理端用：订单不存在就报错，不做归属校验 */
    private Order requireExists(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private OrderView view(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        if (OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            fillReviewed(items);
        }
        return OrderView.full(order, OrderStatus.valueOf(order.getStatus()).label(), items);
    }

    /** 已评价标记批量回填；空列表直接返回，避免拼出 IN () */
    private void fillReviewed(List<OrderItem> items) {
        if (items.isEmpty()) return;
        Set<Long> reviewedIds = new HashSet<>(reviewMapper.findReviewedOrderItemIds(
                items.stream().map(OrderItem::getId).toList()));
        for (OrderItem item : items) {
            item.setReviewed(reviewedIds.contains(item.getId()));
        }
    }

    private Order requireOwned(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        // 别人的订单一律当「不存在」，不泄露数据存在性
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /** status 是逗号分隔的枚举名；空或 null 表示不过滤 */
    private List<String> parseStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : status.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                // 用枚举校验：拼错的状态名要变成 400，而不是被当成「没有订单」
                result.add(OrderStatus.valueOf(value.toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "无效的订单状态：" + value);
            }
        }
        return result;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
