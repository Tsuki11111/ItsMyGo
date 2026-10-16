package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.service.impl.StockServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存击穿：热点 **SKU** 的库存键刚过期时，如果 N 个并发请求同时发现键不存在、
 * 又同时去查库，Redis 这层就等于白加 —— 这正是本测试要盯住的场景。
 *
 * 做法：把 ProductSkuMapper 换成一个「会计数、且故意查得很慢」的替身，
 * 清掉库存键后并发 20 个请求，断言**数据库只被查了一次**。
 *
 * 刻意不加 @Transactional：要真实提交，并发线程才看得到数据。
 */
@SpringBootTest
class StockCacheBreakdownTest {

    private static final String KEY_PREFIX = "its-my-go:stock:sku:";
    private static final int CONCURRENCY = 20;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    private Long createdCategoryId;
    private Long createdProductId;
    private Long createdSkuId;

    @Test
    void onlyOneThreadLoadsFromDatabaseWhenCacheIsCold() throws Exception {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("BD" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("Hot " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(100);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();

        // ⚠️ 直插库的商品不会自动有 SKU（只有走管理端 create 接口才会），
        //    而库存的真相挂在 SKU 上 —— 少了这一步，后面 tryReserve 会因键加载不出来直接返回 false
        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSpecValueIds("");
        sku.setSpecText("");
        sku.setPrice(product.getPrice());
        sku.setStock(product.getStock());
        sku.setSales(0);
        sku.setImage("");
        sku.setStatus(1);
        skuMapper.insert(sku);
        createdSkuId = sku.getId();

        // 把缓存清空，制造「冷启动」这一刻
        redisTemplate.delete(KEY_PREFIX + createdSkuId);
        redisTemplate.delete(KEY_PREFIX + createdSkuId + ":loading");

        AtomicInteger dbQueries = new AtomicInteger();
        ProductSkuMapper countingMapper = Mockito.mock(ProductSkuMapper.class);
        Mockito.when(countingMapper.selectById(Mockito.any())).thenAnswer(invocation -> {
            dbQueries.incrementAndGet();
            Thread.sleep(200);   // 故意慢，放大击穿窗口
            return skuMapper.selectById(invocation.getArgument(0));
        });

        StockServiceImpl service = new StockServiceImpl(redisTemplate, countingMapper);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);

        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.tryReserve(createdSkuId, 1);
                } catch (Exception ignored) {
                    // 等待超时的线程会拿不到缓存，按库存不足处理，这里不关心
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 核心断言：20 个并发请求，数据库只被查了 1 次
        assertThat(dbQueries.get()).isEqualTo(1);
    }

    @AfterEach
    void cleanup() {
        if (createdSkuId != null) {
            redisTemplate.delete(KEY_PREFIX + createdSkuId);
            redisTemplate.delete(KEY_PREFIX + createdSkuId + ":loading");
            skuMapper.deleteById(createdSkuId);
        }
        if (createdProductId != null) {
            productMapper.deleteById(createdProductId);
        }
        if (createdCategoryId != null) {
            categoryMapper.deleteById(createdCategoryId);
        }
    }
}
