package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.service.OrderSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * CREATING 订单超时兜底（M31 / D1）：
 * 事件丢失 / 消费失败时，重新投递补账事件（最多 N 次），仍失败则转 CREATE_FAILED。
 *
 * <p>Redis SETNX 锁保证同一轮只有一个实例执行；测试与无 MQ 环境可关闭。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "itsmygo.order-create", name = "scan-enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderCreationScanJob {

    private static final String LOCK_KEY = RedisCacheHelper.key("order-create:scan-lock");
    private static final int BATCH = 50;

    private final OrderCreateProperties properties;
    private final OrderMapper orderMapper;
    private final OrderSettlementService settlementService;
    private final ObjectProvider<OrderCreatedSender> senderProvider;
    private final StringRedisTemplate redisTemplate;

    public OrderCreationScanJob(OrderCreateProperties properties,
                                OrderMapper orderMapper,
                                OrderSettlementService settlementService,
                                ObjectProvider<OrderCreatedSender> senderProvider,
                                StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.orderMapper = orderMapper;
        this.settlementService = settlementService;
        this.senderProvider = senderProvider;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${itsmygo.order-create.scan-interval-ms:60000}")
    public void scan() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                LOCK_KEY, "1", Duration.ofSeconds(properties.scanLockTtlSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            List<Order> stuck = orderMapper.selectCreatingBefore(
                    properties.creatingTimeoutSeconds(), BATCH);
            for (Order order : stuck) {
                handle(order);
            }
        } catch (RuntimeException e) {
            log.warn("CREATING 订单扫描失败: {}", e.getMessage());
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }

    private void handle(Order order) {
        if (order.getCreateAttempts() >= properties.maxRepublish()) {
            settlementService.failCreation(List.of(order.getId()), "订单创建超时，请重新下单");
            return;
        }
        orderMapper.incrementCreateAttempts(order.getId());
        if (properties.async()) {
            OrderCreatedSender sender = senderProvider.getIfAvailable();
            if (sender != null) {
                sender.send(new OrderCreatedEvent(List.of(order.getId()), List.of()));
                log.info("CREATING 订单重投补账事件: orderNo={} attempts={}",
                        order.getOrderNo(), order.getCreateAttempts() + 1);
            }
            return;
        }
        try {
            settlementService.settle(List.of(order.getId()), List.of());
        } catch (RuntimeException e) {
            settlementService.failCreation(List.of(order.getId()),
                    "补账失败：" + e.getMessage());
        }
    }
}
