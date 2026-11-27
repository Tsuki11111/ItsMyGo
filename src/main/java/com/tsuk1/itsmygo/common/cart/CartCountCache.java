package com.tsuk1.itsmygo.common.cart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 购物车角标（顶栏数量）的 Redis Hash 缓存。
 *
 * <p>只服务 {@code GET /api/cart/count} 这一条高频轮询；购物车页与下单链路不读它，
 * 始终以 MySQL 为准。所以它退化成「角标最多旧一个 TTL」，不会影响成交。</p>
 *
 * <p>结构（{@code its-my-go:cart:count:{userId}}）：</p>
 * <pre>
 * count    -> 所有行数量之和
 * builtAt  -> 重建时间（毫秒，排查用）
 * sku:{id} -> 该规格在购物车里的数量
 * </pre>
 *
 * <p>写操作与下单补账只删 key，不更新缓存；下一次轮询回源重建。</p>
 */
@Slf4j
@Component
public class CartCountCache {

    private static final String KEY_PREFIX = "its-my-go:cart:count:";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_BUILT_AT = "builtAt";
    private static final String SKU_FIELD_PREFIX = "sku:";

    /** 加载锁的存活时间：只防「持有者崩了导致死锁」，要远小于一次业务耗时 */
    private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(5);
    private static final long WAIT_INTERVAL_MILLIS = 50;
    private static final int MAX_WAIT_ROUNDS = 10;

    /**
     * 整份重建放在一个 Lua 里原子执行。
     *
     * <p>拆成多条命令有兩個问题：并发读会看到「count 写了、sku 字段还没写完」的半成品；
     * 更严重的是 EXPIRE 必须和写入同生共死 —— 共用 Redis 是 volatile-lru，
     * 中间崩溃留下不带 TTL 的 key，内存打满时会让所有写入失败。</p>
     *
     * <p>ARGV[1]=count，ARGV[2]=builtAt，ARGV[3..n-1]=成对的 field/value，ARGV[n]=TTL 秒。</p>
     */
    private static final RedisScript<Long> REBUILD_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            redis.call('DEL', key)
            redis.call('HSET', key, 'count', ARGV[1])
            redis.call('HSET', key, 'builtAt', ARGV[2])
            for i = 3, #ARGV - 1, 2 do
                redis.call('HSET', key, ARGV[i], ARGV[i + 1])
            end
            redis.call('EXPIRE', key, tonumber(ARGV[#ARGV]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final CartCountProperties properties;

    public CartCountCache(StringRedisTemplate redisTemplate, CartCountProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 读角标：命中直接返回；未命中时用 {@code loader} 回源并重建。
     *
     * <p>读命中**不续期**：前端 30 秒轮询一次，如果读也续期，活跃用户的缓存永远不过期，
     * 角标会一直停留在第一次的值。过期后由下一轮轮询重建。</p>
     */
    public long count(Long userId, Supplier<CartCountSnapshot> loader) {
        String key = key(userId);
        String lockKey = key + ":loading";
        boolean locked = false;
        try {
            Long cached = readCount(key);
            if (cached != null) {
                return cached;
            }

            locked = Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOAD_LOCK_TTL));
            if (locked) {
                // 双重检查：抢锁期间别人可能已经重建好了
                Long again = readCount(key);
                if (again != null) {
                    return again;
                }
                CartCountSnapshot snapshot = loader.get();
                rebuild(key, snapshot);
                return snapshot.count();
            }

            for (int round = 0; round < MAX_WAIT_ROUNDS; round++) {
                sleep();
                Long loaded = readCount(key);
                if (loaded != null) {
                    return loaded;
                }
            }
            log.warn("购物车角标缓存等待超时，本次直接回源: userId={}", userId);
            return loader.get().count();
        } catch (RuntimeException e) {
            // 缓存永远不能成为购物车的单点：Redis 不可用时退化为直查数据库
            log.warn("购物车角标缓存不可用，降级直查数据库: userId={} err={}", userId, e.getMessage());
            return loader.get().count();
        } finally {
            if (locked) {
                releaseLock(lockKey);
            }
        }
    }

    /** 购物车行变化后调用 —— 必须在对应的数据库写成功之后 */
    public void evict(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (RuntimeException e) {
            // 最坏情况是角标旧一个 TTL；不能让缓存故障阻断加购 / 下单
            log.warn("清理购物车角标缓存失败: userId={} err={}", userId, e.getMessage());
        }
    }

    private Long readCount(String key) {
        Object value = redisTemplate.opsForHash().get(key, FIELD_COUNT);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            // 格式变更或人工改坏：删掉重建，别让角标请求失败
            log.warn("购物车角标缓存内容损坏，删除重建: key={}", key);
            redisTemplate.delete(key);
            return null;
        }
    }

    private void rebuild(String key, CartCountSnapshot snapshot) {
        List<String> args = new ArrayList<>(3 + snapshot.skuQuantities().size() * 2);
        args.add(String.valueOf(snapshot.count()));
        args.add(String.valueOf(System.currentTimeMillis()));
        for (Map.Entry<Long, Integer> entry : snapshot.skuQuantities().entrySet()) {
            args.add(SKU_FIELD_PREFIX + entry.getKey());
            args.add(String.valueOf(entry.getValue()));
        }
        args.add(String.valueOf(ttlWithJitter().toSeconds()));
        redisTemplate.execute(REBUILD_SCRIPT, List.of(key), args.toArray());
    }

    /** ±20% 抖动：防止一批用户的角标在同一秒集体过期（缓存雪崩） */
    private Duration ttlWithJitter() {
        long baseSeconds = properties.getTtl().toSeconds();
        long jitter = ThreadLocalRandom.current()
                .nextLong(-baseSeconds / 5, baseSeconds / 5 + 1);
        return Duration.ofSeconds(Math.max(1, baseSeconds + jitter));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException e) {
            // 锁自带 5 秒 TTL，删不掉也会自己过期
            log.debug("释放购物车角标加载锁失败: {}", lockKey);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
