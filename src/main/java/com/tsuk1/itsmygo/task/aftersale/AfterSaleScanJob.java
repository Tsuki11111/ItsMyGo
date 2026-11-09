package com.tsuk1.itsmygo.task.aftersale;

import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.after_sale.AfterSaleProperties;
import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.service.AfterSaleService;
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
 * 售后超时扫描（M30）：
 * <ul>
 *   <li>{@code PENDING_REVIEW} 超过 {@code review-hours} 未审核 → 自动同意；</li>
 *   <li>{@code RETURN_SHIPPED} 超过 {@code return-confirm-days} 商家未确认收货 → 自动确认并退款。</li>
 * </ul>
 *
 * <p>多实例下用 Redis SETNX 锁保证同一轮只有一个实例执行（锁必须带 TTL，
 * 否则共用 Redis 的 volatile-lru 策略下会有内存风险）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "itsmygo.after-sale", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AfterSaleScanJob {

    private static final String LOCK_KEY = RedisCacheHelper.key("after-sale:scan-lock");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final AfterSaleService afterSaleService;
    private final StringRedisTemplate redisTemplate;
    private final AfterSaleProperties properties;

    public AfterSaleScanJob(AfterSaleService afterSaleService,
                            StringRedisTemplate redisTemplate,
                            AfterSaleProperties properties) {
        this.afterSaleService = afterSaleService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${itsmygo.after-sale.scan-interval-ms:300000}")
    public void scan() {
        MDC.put(RequestIdFilter.TRACE_ID,
                "aftersale-scan-" + UUID.randomUUID().toString().replace("-", ""));
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
            log.warn("售后超时扫描抢锁失败，跳过本轮: {}", e.getMessage());
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int approved = afterSaleService.autoApproveExpired(properties.reviewHours());
            int confirmed = afterSaleService.autoConfirmReturnExpired(properties.returnConfirmDays());
            if (approved > 0 || confirmed > 0) {
                log.info("售后超时扫描：自动同意 {} 单、自动确认收货 {} 单", approved, confirmed);
            }
        } catch (RuntimeException e) {
            log.warn("售后超时扫描失败: {}", e.getMessage());
        } finally {
            release(token);
        }
    }

    private void release(String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
        } catch (RuntimeException e) {
            log.warn("释放售后扫描锁失败（TTL 会自动过期）: {}", e.getMessage());
        }
    }
}
