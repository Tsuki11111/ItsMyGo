package com.tsuk1.itsmygo.common;

import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品布隆过滤器 —— 挡「查不存在的商品」这类请求，防缓存穿透。
 *
 * 缓存穿透与缓存击穿是两回事：击穿是**热 key 过期**导致并发回源；
 * 穿透是查询**压根不存在的数据**，缓存永远不命中，每次都打数据库。
 *
 * 布隆过滤器的特性：说「不存在」一定准，说「存在」有小概率误判（假阳性）。
 * 所以它只能用来**挡掉确定不存在的请求**，命中的仍要正常查库。
 *
 * 用 Redis bitmap 自己实现（而不是 Guava 本地布隆过滤器）：
 * 本地布隆过滤器在**多实例部署下各存一份**，商品上下架后无法同步，
 * 且重启就丢。放 Redis 里天然是分布式共享的。
 *
 * 误判率：{@code BIT_SIZE} = 2^20 位（128 KB），商品数远小于此，
 * 配 6 个 hash 函数，万级商品量下误判率在千分之一量级，够用。
 */
@Slf4j
@Component
public class ProductBloomFilter implements ApplicationRunner {

    private static final String KEY = "its-my-go:bloom:product";
    private static final long BIT_SIZE = 1L << 20;
    private static final int HASH_COUNT = 6;

    /** 布隆过滤器本身没 TTL，但共用 Redis 是 volatile-lru；给它一个长 TTL 以防被当成垃圾键淘汰 */
    private static final Duration TTL = Duration.ofDays(7);

    /** 一次往返把 6 个 bit 全写上并续期，避免 6 次网络调用。ARGV[1] 是 TTL，其后是各位的偏移 */
    private static final RedisScript<List> ADD_SCRIPT = new DefaultRedisScript<>("""
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            for i = 2, #ARGV do
                redis.call('SETBIT', KEYS[1], ARGV[i], 1)
            end
            return {1}
            """, List.class);

    private static final RedisScript<List> MIGHT_CONTAIN_SCRIPT = new DefaultRedisScript<>("""
            for i = 1, #ARGV do
                if redis.call('GETBIT', KEYS[1], ARGV[i]) == 0 then
                    return {0}
                end
            end
            return {1}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    public ProductBloomFilter(StringRedisTemplate redisTemplate, ProductMapper productMapper) {
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
    }

    /**
     * 启动时把库里已有的商品 ID 灌进去。
     * 不做这一步，重启后（或 Redis 过期后）过滤器是空的，
     * 会**把所有请求都判为「不存在」** —— 比没有过滤器还糟。
     */
    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        try {
            List<Product> products = productMapper.selectList(null);
            if (products.isEmpty()) {
                return;
            }
            for (Product product : products) {
                add(product.getId());
            }
            log.info("布隆过滤器已预热 {} 个商品 ID", products.size());
        } catch (Exception e) {
            // 预热失败不该拦住应用启动；之后新商品仍会被增量加入
            log.warn("布隆过滤器预热失败，将退化为不放行过滤: {}", e.getMessage());
        }
    }

    public void add(Long productId) {
        if (productId == null) {
            return;
        }
        List<String> values = new ArrayList<>(HASH_COUNT + 1);
        values.add(String.valueOf(TTL.toSeconds()));
        for (int i = 0; i < HASH_COUNT; i++) {
            values.add(String.valueOf(index(productId, i)));
        }
        redisTemplate.execute(ADD_SCRIPT, List.of(KEY), values.toArray());
    }

    /** @return false 表示**一定不存在**（可以放心拒掉）；true 只是「可能存在」，仍需查库 */
    public boolean mightContain(Long productId) {
        if (productId == null) {
            return false;
        }
        List<String> values = new ArrayList<>(HASH_COUNT);
        for (int i = 0; i < HASH_COUNT; i++) {
            values.add(String.valueOf(index(productId, i)));
        }
        List<?> result = redisTemplate.execute(MIGHT_CONTAIN_SCRIPT, List.of(KEY), values.toArray());
        return result != null && !result.isEmpty() && "1".equals(String.valueOf(result.get(0)));
    }

    /** 双重哈希：h(i) = mix(id + i × 大素数)，全部落在 [0, BIT_SIZE) */
    private long index(Long productId, int seed) {
        long h = productId + (long) seed * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return Math.abs(h % BIT_SIZE);
    }
}
