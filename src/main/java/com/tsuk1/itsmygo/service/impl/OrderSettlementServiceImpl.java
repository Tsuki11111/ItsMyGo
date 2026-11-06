package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.order.OrderTimeoutPublisher;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.UserCouponMapper;
import com.tsuk1.itsmygo.service.OrderSettlementService;
import com.tsuk1.itsmygo.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下单补账实现（M31 / D1）。
 *
 * <p>顺序很关键：**先 CAS 抢订单（CREATING → PENDING_PAYMENT），再扣库存**。
 * 这样同一订单被重复消费时，只有抢到 CAS 的那次会真正扣减；否则两个消费者会各扣一次。
 * 任何一步失败都抛异常回滚，由调用方（消费端 / 扫描任务）转 {@code failCreation}。</p>
 */
@Slf4j
@Service
public class OrderSettlementServiceImpl implements OrderSettlementService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final UserCouponMapper userCouponMapper;
    private final CartItemMapper cartItemMapper;
    private final StockService stockService;
    private final OrderTimeoutPublisher orderTimeoutPublisher;

    public OrderSettlementServiceImpl(OrderMapper orderMapper,
                                      OrderItemMapper orderItemMapper,
                                      ProductSkuMapper skuMapper,
                                      ProductMapper productMapper,
                                      UserCouponMapper userCouponMapper,
                                      CartItemMapper cartItemMapper,
                                      StockService stockService,
                                      OrderTimeoutPublisher orderTimeoutPublisher) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.userCouponMapper = userCouponMapper;
        this.cartItemMapper = cartItemMapper;
        this.stockService = stockService;
        this.orderTimeoutPublisher = orderTimeoutPublisher;
    }

    @Override
    @Transactional
    public List<Order> settle(List<Long> orderIds, List<Long> cartItemIds) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .in(Order::getId, orderIds)
                .eq(Order::getStatus, OrderStatus.CREATING.name())
                .orderByAsc(Order::getId));
        if (orders.isEmpty()) {
            return List.of();
        }

        // ① 先抢订单：CAS 成功才轮到本次扣库存 / 占券；重复消费会在这里被挡掉
        List<Order> won = new ArrayList<>(orders.size());
        for (Order order : orders) {
            if (orderMapper.compareAndSetStatus(order.getId(),
                    OrderStatus.CREATING.name(), OrderStatus.PENDING_PAYMENT.name()) == 0) {
                continue;
            }
            order.setStatus(OrderStatus.PENDING_PAYMENT.name());
            won.add(order);
        }
        if (won.isEmpty()) {
            return List.of();
        }

        // ② 按 SKU 汇总扣减（同一请求拆成多单时，同一 SKU 可能出现在多张订单里）
        List<Long> wonIds = won.stream().map(Order::getId).toList();
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .in(OrderItem::getOrderId, wonIds));
        Map<Long, Integer> skuQuantities = new LinkedHashMap<>();
        Map<Long, Long> skuProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            skuQuantities.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
            skuProduct.put(item.getSkuId(), item.getProductId());
        }
        for (Map.Entry<Long, Integer> entry : skuQuantities.entrySet()) {
            Long skuId = entry.getKey();
            int quantity = entry.getValue();
            if (skuMapper.deductStock(skuId, quantity) == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
            }
            productMapper.applyDeducted(skuProduct.get(skuId), quantity);
        }

        // ③ 占用券：条件更新失败说明券已被别的订单用掉 / 失效
        for (Order order : won) {
            if (order.getUserCouponId() != null
                    && userCouponMapper.markUsed(order.getUserCouponId(), order.getUserId(),
                    order.getId()) == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "优惠券已使用或已过期");
            }
        }

        // ④ 清购物车：只删事件里快照的行 ID，避免误删后来加入的商品
        if (cartItemIds != null && !cartItemIds.isEmpty()) {
            cartItemMapper.deleteBatchIds(cartItemIds);
        }

        // ⑤ 事务提交后才发超时关单消息
        orderTimeoutPublisher.publishAfterCommit(won);
        log.info("下单补账完成: orderIds={} skuCount={}", wonIds, skuQuantities.size());
        return won;
    }

    @Override
    @Transactional
    public void failCreation(List<Long> orderIds, String reason) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .in(Order::getId, orderIds)
                .eq(Order::getStatus, OrderStatus.CREATING.name()));
        for (Order order : orders) {
            if (orderMapper.compareAndSetStatus(order.getId(),
                    OrderStatus.CREATING.name(), OrderStatus.CREATE_FAILED.name()) == 0) {
                continue;
            }
            String text = reason == null ? "" : reason;
            if (text.length() > 200) {
                text = text.substring(0, 200);
            }
            orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                    .eq(Order::getId, order.getId())
                    .set(Order::getCreateError, text));

            // 回补 Redis 预扣（CAS 抢到的这次才补，保证幂等）
            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderId, order.getId()));
            for (OrderItem item : items) {
                stockService.release(item.getSkuId(), item.getQuantity());
            }
            // 券在补账事务里若已占用会随之回滚；这里兜底调一次（幂等）
            try {
                userCouponMapper.restoreByOrderId(order.getId());
            } catch (RuntimeException e) {
                log.warn("订单创建失败回滚券失败: orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
            log.warn("订单创建失败: orderNo={} reason={}", order.getOrderNo(), text);
        }
    }
}
