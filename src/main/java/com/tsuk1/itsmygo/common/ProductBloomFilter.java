package com.tsuk1.itsmygo.common;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
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
        addTo(KEY, productId);
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

    /**
     * 全量重建 —— 用于「有商品没走 create 接口入库」的场景（数据迁移、手工 SQL、导入脚本）。
     *
     * **建到临时 key 再 RENAME 切换，绝不能「先删再建」**：
     * 先删的话，重建完成前过滤器是空的，那一小段时间里**所有商品都会被判为「不存在」**，
     * 比不重建还糟。
     */
    public int rebuild() {
        String tempKey = KEY + ":rebuilding";
        redisTemplate.delete(tempKey);

        int total = 0;
        long lastId = 0;
        while (true) {
            // 按主键游标分批取，避免一次性把全表读进内存
            List<Product> batch = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                    .select(Product::getId)
                    .gt(Product::getId, lastId)
                    .orderByAsc(Product::getId)
                    .last("LIMIT 1000"));
            if (batch.isEmpty()) {
                break;
            }
            for (Product product : batch) {
                addTo(tempKey, product.getId());
                lastId = product.getId();
                total++;
            }
        }

        if (total > 0) {
            redisTemplate.rename(tempKey, KEY);   // 原子切换
        } else {
            redisTemplate.delete(tempKey);
        }
        log.info("布隆过滤器重建完成，共 {} 个商品 ID", total);
        return total;
    }

    private void addTo(String key, Long productId) {
        List<String> values = new ArrayList<>(HASH_COUNT + 1);
        values.add(String.valueOf(TTL.toSeconds()));
        for (int i = 0; i < HASH_COUNT; i++) {
            values.add(String.valueOf(index(productId, i)));
        }
        redisTemplate.execute(ADD_SCRIPT, List.of(key), values.toArray());
    }

    /**
     * 兜底：即使有人手工插库后忘了调重建接口，每天也会自动修一次。
     *
     * ⚠️ 多实例部署时这个任务会在每个实例上各跑一遍。目前项目是单体所以无所谓；
     * 将来上多实例要么加分布式锁，要么只在指定实例上执行。
     */
    @Scheduled(cron = "0 17 4 * * ?")
    public void scheduledRebuild() {
        try {
            rebuild();
        } catch (Exception e) {
            // 定时任务抛异常会让后续调度静默失效，这里必须自己兜住
            log.error("定时重建布隆过滤器失败", e);
        }
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
