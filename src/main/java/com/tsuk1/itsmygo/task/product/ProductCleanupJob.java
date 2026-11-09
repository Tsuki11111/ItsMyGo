package com.tsuk1.itsmygo.task.product;

import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.product.ProductCleanupProperties;
import com.tsuk1.itsmygo.service.ProductCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 长期下架商品清理任务：默认关闭；开启后按 dryRun 决定只出报告还是真执行。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "itsmygo.product-cleanup.enabled", havingValue = "true")
public class ProductCleanupJob {

    private static final String LOCK_KEY = RedisCacheHelper.key("product-cleanup:scan-lock");

    private final ProductCleanupService cleanupService;
    private final ProductCleanupProperties properties;
    private final StringRedisTemplate redisTemplate;

    public ProductCleanupJob(ProductCleanupService cleanupService,
                             ProductCleanupProperties properties,
                             StringRedisTemplate redisTemplate) {
        this.cleanupService = cleanupService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${itsmygo.product-cleanup.scan-interval-ms:86400000}")
    public void scan() {
        String token = UUID.randomUUID().toString();
        Boolean locked;
        try {
            locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token,
                    Duration.ofSeconds(properties.getScanLockTtlSeconds()));
        } catch (RuntimeException e) {
            log.warn("商品清理扫描抢锁异常，跳过本轮: {}", e.getMessage());
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            log.info("商品清理扫描已有实例在跑，跳过本轮");
            return;
        }
        try {
            cleanupService.run(properties.isDryRun());
        } catch (RuntimeException e) {
            log.warn("商品清理扫描失败: {}", e.getMessage());
        } finally {
            try {
                String current = redisTemplate.opsForValue().get(LOCK_KEY);
                if (token.equals(current)) {
                    redisTemplate.delete(LOCK_KEY);
                }
            } catch (RuntimeException ignored) {
                // 锁会自动过期
            }
        }
    }
}
