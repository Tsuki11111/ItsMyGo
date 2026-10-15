package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.ProductBloomFilter;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.StockService;
import com.tsuk1.itsmygo.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 缓存防护的两项：
 *   - **穿透**：查压根不存在的数据，缓存永不命中，每次都打数据库 → 布隆过滤器挡在前面
 *   - **雪崩**：大量键同时过期，瞬间全部回源 → TTL 加随机抖动错开
 *
 * 与 `StockCacheBreakdownTest`（击穿：热 key 过期导致并发回源）是三件不同的事。
 */
@SpringBootTest
class CacheProtectionTest {

    private static final String STOCK_KEY_PREFIX = "its-my-go:stock:";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductBloomFilter bloomFilter;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private StockService stockService;

    private Long createdCategoryId;
    private Long createdProductId;

    /** 布隆过滤器说「不存在」时，必须**一次数据库都不查** */
    @Test
    void nonExistentProductIsRejectedWithoutTouchingDatabase() {
        AtomicInteger dbQueries = new AtomicInteger();
        ProductMapper countingMapper = Mockito.mock(ProductMapper.class);
        Mockito.when(countingMapper.selectById(Mockito.any())).thenAnswer(invocation -> {
            dbQueries.incrementAndGet();
            return null;
        });

        ProductServiceImpl service = new ProductServiceImpl(countingMapper, bloomFilter);

        long ghostId = 999_999_999L;
        assertThatThrownBy(() -> service.detailForCustomer(ghostId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品不存在");

        assertThat(dbQueries.get()).isZero();
    }

    /** 真实存在的商品必须能通过过滤器（否则过滤器会把正常流量也挡掉） */
    @Test
    void existingProductPassesTheFilter() throws Exception {
        createProduct();

        assertThat(bloomFilter.mightContain(createdProductId)).isTrue();

        AtomicInteger dbQueries = new AtomicInteger();
        ProductMapper realMapper = productMapper;
        ProductMapper countingMapper = Mockito.mock(ProductMapper.class);
        Mockito.when(countingMapper.selectById(Mockito.any())).thenAnswer(invocation -> {
            dbQueries.incrementAndGet();
            return realMapper.selectById(invocation.getArgument(0));
        });

        ProductServiceImpl service = new ProductServiceImpl(countingMapper, bloomFilter);
        assertThat(service.detailForCustomer(createdProductId).getId()).isEqualTo(createdProductId);
        assertThat(dbQueries.get()).isEqualTo(1);
    }

    /** TTL 抖动：连续刷新同一个商品，过期时间不应是同一个固定值 */
    @Test
    void stockTtlIsJitteredInsteadOfFixed() throws Exception {
        createProduct();

        String key = STOCK_KEY_PREFIX + createdProductId;
        Set<Long> observedTtls = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            stockService.reload(createdProductId);
            observedTtls.add(redisTemplate.getExpire(key));
        }

        // 固定 TTL 的话这里只会有一个值
        assertThat(observedTtls).hasSizeGreaterThan(1);
    }

    private void createProduct() {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("BP" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("Bloom " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(100);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();

        // 模拟 ProductServiceImpl.create 里的增量加入
        bloomFilter.add(createdProductId);
    }

    @AfterEach
    void cleanup() {
        if (createdProductId != null) {
            redisTemplate.delete(STOCK_KEY_PREFIX + createdProductId);
            productMapper.deleteById(createdProductId);
        }
        if (createdCategoryId != null) {
            categoryMapper.deleteById(createdCategoryId);
        }
    }
}
