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

    /** setIfAbsent 而非 set：并发加载时只有第一个写入生效，避免互相覆盖 */
    private void loadIfAbsent(Long productId, String key) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return;
        }
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(product.getStock()), STOCK_TTL);
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
