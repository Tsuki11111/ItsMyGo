package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 库存的两层防线：
 *   第一层 Redis —— 用 Lua 脚本原子地「判断 + 扣减」，把绝大多数并发请求挡在数据库之外
 *   第二层 数据库 —— {@code ProductMapper.deductStock} 的 {@code WHERE stock >= ?} 是最终真相
 *
 * Redis 这层不追求与数据库强一致：键丢了就从库里重新加载，
 * 库存对不上也只是「预扣判断偏保守/偏乐观」，最终由数据库条件更新裁决。
 */
@Slf4j
@Service
public class StockServiceImpl implements StockService {

    private static final String KEY_PREFIX = "its-my-go:stock:";
    /** 硬约束：共用 Redis 是 volatile-lru 策略，不带 TTL 的 key 在内存打满时会让写入直接失败 */
    private static final Duration STOCK_TTL = Duration.ofMinutes(30);

    /** 加载锁的存活时间：只是防「持有者崩了导致死锁」，要远小于一次业务耗时 */
    private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(5);
    private static final long WAIT_INTERVAL_MILLIS = 50;
    /** 最多等 500ms；超时说明 Redis 或数据库出了问题，宁可快速失败也不放过去打数据库 */
    private static final int MAX_WAIT_ROUNDS = 10;

    /**
     * KEYS[1] = 库存键，ARGV[1] = 本次要扣的数量
     * 返回：扣减后的余量（>= 0），或 -1 表示「键不存在」/「库存不足」
     */
    private static final RedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then
                return -1
            end
            if tonumber(stock) < tonumber(ARGV[1]) then
                return -1
            end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    public StockServiceImpl(StringRedisTemplate redisTemplate, ProductMapper productMapper) {
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
    }

    @Override
    public boolean tryReserve(Long productId, int quantity) {
        String key = key(productId);
        loadIfAbsent(productId, key);

        Long remain = redisTemplate.execute(RESERVE_SCRIPT, List.of(key), String.valueOf(quantity));
        return remain != null && remain >= 0;
    }

    @Override
    public void release(Long productId, int quantity) {
        String key = key(productId);
        // 键可能已过期，直接 INCRBY 会凭空造一个库存键 —— 所以只补已存在的
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().increment(key, quantity);
            redisTemplate.expire(key, STOCK_TTL);
        }
    }

    @Override
    public void reload(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            redisTemplate.opsForValue().set(key(productId), String.valueOf(product.getStock()), STOCK_TTL);
        }
    }

    /**
     * 把库存从数据库灌进 Redis，**整个集群同时只允许一个线程去查库**。
     *
     * 不这么做的话：热点商品的库存键刚过期的那一瞬间，N 个并发请求会同时
     * 发现键不存在，然后**一起打向数据库** —— 这就是缓存击穿，
     * 而「不让请求打到数据库」恰恰是这层缓存存在的意义。
     * {@code setIfAbsent} 只能保证写入唯一，挡不住重复查询。
     */
    private void loadIfAbsent(Long productId, String key) {
        if (isLoaded(key)) {
            return;
        }

        String lockKey = key + ":loading";
        // SETNX + TTL 一条命令完成，别拆成 SETNX 再 EXPIRE（两步之间崩掉就是死锁）
        Boolean gotLock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOAD_LOCK_TTL);

        if (Boolean.TRUE.equals(gotLock)) {
            try {
                // 双重检查：抢锁这段时间里，上一个持锁者可能已经把数据写好了
                if (isLoaded(key)) {
                    return;
                }
                Product product = productMapper.selectById(productId);
                if (product != null) {
                    redisTemplate.opsForValue().set(key, String.valueOf(product.getStock()), STOCK_TTL);
                }
            } finally {
                // 放锁一定要在 finally 里，否则中途抛异常锁会一直留到 TTL 到期
                redisTemplate.delete(lockKey);
            }
            return;
        }

        // 没抢到锁 —— 说明别人正在加载，**绝不能也去查库**，自旋等它写完即可
        awaitLoaded(key);
    }

    private void awaitLoaded(String key) {
        for (int round = 0; round < MAX_WAIT_ROUNDS; round++) {
            try {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (isLoaded(key)) {
                return;
            }
        }
        // 等不到就放弃：后续 Lua 会因键不存在返回 -1，上层按「库存不足」处理
        log.warn("等待库存缓存加载超时: {}", key);
    }

    private boolean isLoaded(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
