package com.tsuk1.itsmygo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Cache-Aside 的通用实现。
 *
 * 读：命中直接返回；未命中时**用互斥锁保护回源**（防击穿），再写回缓存。
 * 写：调用方在**更新数据库之后**调 {@link #evict} 删缓存 —— 是删不是更新，
 *     因为并发更新时「写缓存的顺序」可能和「写库的顺序」相反，更新缓存会永久脏。
 *
 * 只有 TTL 是最终兜底：删缓存那一步失败时，最多脏一个 TTL 周期。
 *
 * ⚠️ 只用于**共享的、非用户私有**的数据（商品详情、分类树）。
 * 购物车、订单这类用户私有且频繁变动的数据不该进缓存。
 */
@Slf4j
@Component
public class RedisCacheHelper {

    /** 加载锁的存活时间：只防「持有者崩了导致死锁」，要远小于一次业务耗时 */
    private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(5);
    private static final long WAIT_INTERVAL_MILLIS = 50;
    private static final int MAX_WAIT_ROUNDS = 10;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheHelper(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String key, Duration baseTtl, Class<T> type, Supplier<T> loader) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (Exception e) {
                // 缓存内容坏了（格式变更、人工改坏）—— 删掉重来，别让请求失败
                log.warn("缓存反序列化失败，将删除重建: {}", key);
                redisTemplate.delete(key);
            }
        }

        String lockKey = key + ":loading";
        Boolean gotLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOAD_LOCK_TTL);

        if (Boolean.TRUE.equals(gotLock)) {
            try {
                // 双重检查：抢锁期间别人可能已经写好了
                String loaded = redisTemplate.opsForValue().get(key);
                if (loaded != null) {
                    return objectMapper.readValue(loaded, type);
                }
                T value = loader.get();
                if (value != null) {
                    redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), jittered(baseTtl));
                }
                return value;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // 没抢到锁 —— 别人正在加载，等它写好即可，绝不能也去回源
        for (int round = 0; round < MAX_WAIT_ROUNDS; round++) {
            sleep();
            String loaded = redisTemplate.opsForValue().get(key);
            if (loaded != null) {
                return objectMapper.readValue(loaded, type);
            }
        }
        // 等不到（Redis 或数据库可能有问题）：直接回源，保证功能可用，不把请求打挂
        log.warn("等待缓存加载超时，本次直接回源: {}", key);
        return loader.get();
    }

    /** 数据变更后调用 —— 删缓存而不是更新缓存 */
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    /** TTL 加随机抖动（±20%），防止一批 key 在同一秒集体过期（缓存雪崩） */
    private Duration jittered(Duration base) {
        long baseSeconds = base.toSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(-baseSeconds / 5, baseSeconds / 5 + 1);
        return Duration.ofSeconds(baseSeconds + jitter);
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 缓存 key 统一前缀，与共用 Redis 上其他项目隔离 */
    public static String key(String suffix) {
        return "its-my-go:" + suffix;
    }
}
