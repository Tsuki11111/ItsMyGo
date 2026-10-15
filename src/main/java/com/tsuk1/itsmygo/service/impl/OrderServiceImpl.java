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
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private static final int ON_SALE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final AddressMapper addressMapper;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                            CartItemMapper cartItemMapper, ProductMapper productMapper,
                            AddressMapper addressMapper, StockService stockService,
                            StringRedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.addressMapper = addressMapper;
        this.stockService = stockService;
        this.redisTemplate = redisTemplate;
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

        List<CartItem> cartItems = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));
        if (cartItems.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先勾选要购买的商品");
        }

        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> products = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 已成功扣减的商品 → 数量；出错时按它回补 Redis
        // （数据库那边由 @Transactional 自动回滚，Redis 不在事务里，必须手动补）
        Map<Long, Integer> reserved = new LinkedHashMap<>();

        try {
            List<OrderItem> items = new ArrayList<>(cartItems.size());
            BigDecimal total = BigDecimal.ZERO;

            for (CartItem cartItem : cartItems) {
                Product product = products.get(cartItem.getProductId());
                if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "商品已下架，请先移出购物车");
                }

                reserveStock(product.getId(), cartItem.getQuantity(), reserved);

                OrderItem item = new OrderItem();
                item.setProductId(product.getId());
                // 以下三项是快照，商品以后改价改名都不影响这张订单
                item.setProductName(product.getName());
                item.setProductImage(product.getMainImage());
                item.setUnitPrice(product.getPrice());
                item.setQuantity(cartItem.getQuantity());
                item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                items.add(item);

                total = total.add(item.getSubtotal());
            }

            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setTotalAmount(total);
            order.setStatus(OrderStatus.PENDING_PAYMENT.name());
            // 收货信息也快照：用户以后改地址或删地址，这张订单不受影响
            order.setReceiver(address.getReceiver());
            order.setPhone(address.getPhone());
            order.setAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail());
            order.setTrackingNo("");
            orderMapper.insert(order);

            for (OrderItem item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 下单成功，把已下单的购物车行清掉
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                    .eq(CartItem::getUserId, userId)
                    .eq(CartItem::getSelected, 1));

            return new OrderView(order, OrderStatus.PENDING_PAYMENT.label(), items);

        } catch (RuntimeException e) {
            reserved.forEach(stockService::release);
            throw e;
        }
    }

    /**
     * 两层防线：
     *   1. Redis 预扣 —— 原子 Lua，把大部分并发挡在数据库外
     *   2. 数据库条件更新 —— {@code WHERE stock >= ?}，防超卖的最终真相
     * 第一层过了第二层没过（例如 Redis 数据偏乐观），照样按库存不足处理。
     */
    private void reserveStock(Long productId, int quantity, Map<Long, Integer> reserved) {
        if (!stockService.tryReserve(productId, quantity)) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }
        reserved.merge(productId, quantity, Integer::sum);

        if (productMapper.deductStock(productId, quantity) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }
    }

    @Override
    public PageResponse<Order> pageForCustomer(Long userId, int page, int size) {
        Page<Order> pageRequest = new Page<>(normalizePage(page), normalizeSize(size));
        return PageResponse.of(orderMapper.selectPage(pageRequest, Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getId)));
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
        return view(order);
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

    /** 取消时把库存和销量都退回去（数据库 + Redis 两层） */
    private void restoreStock(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
            stockService.release(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 状态流转的唯一入口：先查状态机允许不允许，再改状态和时间戳。
     * 非法流转在这里被挡住（例如「已取消」的订单不能被发货）。
     */
    private void transition(Order order, OrderStatus target, java.util.function.Consumer<LocalDateTime> stampSetter) {
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单当前是「" + current.label() + "」，不能变成「" + target.label() + "」");
        }
        order.setStatus(target.name());
        stampSetter.accept(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private OrderView view(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        return new OrderView(order, OrderStatus.valueOf(order.getStatus()).label(), items);
    }

    private Order requireOwned(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        // 别人的订单一律当「不存在」，不泄露数据存在性
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private String generateOrderNo() {
        return ORDER_NO_TIME.format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
