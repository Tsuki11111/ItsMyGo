package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 超时关单的兜底扫描：MQ 消息丢失、broker 不可用或发送失败时，仍能定期取消。
 *
 * <p>多实例下用 Redis SETNX 锁保证同一轮只有一个实例执行；锁必须带 TTL。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "itsmygo.order-timeout", name = "scan-enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderTimeoutScanJob {

    private static final String LOCK_KEY = RedisCacheHelper.key("order-timeout:scan-lock");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;
    private final OrderTimeoutProperties properties;

    public OrderTimeoutScanJob(OrderService orderService,
                               StringRedisTemplate redisTemplate,
                               OrderTimeoutProperties properties) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${itsmygo.order-timeout.scan-interval-ms:300000}")
    public void scan() {
        MDC.put(RequestIdFilter.TRACE_ID,
                "scan-" + UUID.randomUUID().toString().replace("-", ""));
        try {
            doScan();
        } finally {
            MDC.remove(RequestIdFilter.TRACE_ID);
        }
    }

    private void doScan() {
        String token = UUID.randomUUID().toString();
        Boolean locked;
        try {
            locked = redisTemplate.opsForValue().setIfAbsent(
                    LOCK_KEY, token, Duration.ofSeconds(properties.scanLockTtlSeconds()));
        } catch (RuntimeException e) {
            log.warn("超时关单扫描抢锁失败，跳过本轮: {}", e.getMessage());
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            int cancelled = orderService.cancelExpired(properties.minutes());
            if (cancelled > 0) {
                log.info("超时关单扫描取消 {} 笔订单（阈值 {} 分钟）",
                        cancelled, properties.minutes());
            }
        } catch (RuntimeException e) {
            log.warn("超时关单扫描失败: {}", e.getMessage());
        } finally {
            release(token);
        }
    }

    private void release(String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
        } catch (RuntimeException e) {
            log.warn("释放超时关单扫描锁失败（TTL 会自动过期）: {}", e.getMessage());
        }
    }
}
