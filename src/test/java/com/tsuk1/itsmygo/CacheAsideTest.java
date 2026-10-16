package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.ProductBloomFilter;
import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.CategoryService;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.ProductSkuService;
import com.tsuk1.itsmygo.service.StockService;
import com.tsuk1.itsmygo.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache-Aside 的两条核心行为：
 *   ① 第二次读走缓存 —— 数据库只被查一次（缓存确实生效了）
 *   ② **改完立刻能看到新值** —— 更新时删缓存，而不是等 TTL 自然过期
 *
 * ② 才是「双写一致性」在代码里的体现。若只写缓存不删，这里会读到旧价格。
 */
@SpringBootTest
class CacheAsideTest {

    private static final String PRODUCT_KEY_PREFIX = "its-my-go:product:";
    private static final String TREE_KEY = "its-my-go:category:tree";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductService productService;
    @Autowired
    private StockService stockService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private RedisCacheHelper cacheHelper;
    @Autowired
    private ProductBloomFilter bloomFilter;
    @Autowired
    private ProductSkuService productSkuService;

    private Long createdCategoryId;
    private Long createdProductId;
    /** 本类不加 @Transactional（要测缓存的真实行为），所有建出来的分类都得自己记着删 */
    private final java.util.List<Long> createdCategoryIds = new java.util.ArrayList<>();

    @Test
    void secondReadHitsCacheInsteadOfDatabase() {
        createProduct();
        // 清掉可能残留的详情缓存，保证这次一定是冷启动
        cacheHelper.evict(PRODUCT_KEY_PREFIX + createdProductId);

        AtomicInteger dbQueries = new AtomicInteger();
        ProductMapper countingMapper = Mockito.mock(ProductMapper.class);
        Mockito.when(countingMapper.selectById(Mockito.any())).thenAnswer(invocation -> {
            dbQueries.incrementAndGet();
            return productMapper.selectById(invocation.getArgument(0));
        });
        // 只把 Mapper 换成计数版，缓存用的还是真实那一个
        ProductServiceImpl countingService =
                new ProductServiceImpl(countingMapper, bloomFilter, cacheHelper, stockService, productSkuService);

        countingService.detailForCustomer(createdProductId);
        countingService.detailForCustomer(createdProductId);
        countingService.detailForCustomer(createdProductId);

        // 三次读，数据库只被查一次
        assertThat(dbQueries.get()).isEqualTo(1);
    }

    /** 双写一致性的核心：改完立刻能读到新值（说明缓存被删了，不是等 TTL 过期） */
    @Test
    void updateInvalidatesCacheImmediately() throws Exception {
        createProduct();

        // 先读一次，把旧值放进缓存
        Product before = productService.detailForCustomer(createdProductId);
        assertThat(before.getPrice()).isEqualByComparingTo("100.00");

        // 改价
        productService.update(createdProductId, new ProductRequest(createdCategoryId,
                "Renamed " + System.nanoTime(), "", "", new BigDecimal("888.00"), 5, "", true));

        // 立刻再读 —— 必须是新价，不能是缓存里的旧价
        Product after = productService.detailForCustomer(createdProductId);
        assertThat(after.getPrice()).isEqualByComparingTo("888.00");
        assertThat(after.getStock()).isEqualTo(5);
    }

    @Test
    void categoryTreeIsCachedAndInvalidatedOnChange() throws Exception {
        createCategory();

        cacheHelper.evict(TREE_KEY);

        // 第一次读会建缓存
        categoryService.tree(true);
        assertThat(redisTemplate.hasKey(TREE_KEY)).isTrue();

        // 新增一个分类后，缓存必须被删掉
        Category extra = categoryService.create(
                new CategoryRequest(null, "New " + System.nanoTime(), 0, true));
        createdCategoryIds.add(extra.getId());   // 本类不加 @Transactional，建了就要自己删
        assertThat(redisTemplate.hasKey(TREE_KEY)).isFalse();

        // 重新读出来的树里能看到新分类
        assertThat(categoryService.tree(true))
                .extracting(node -> node.name())
                .anyMatch(name -> name.startsWith("New "));
    }

    private void createProduct() {
        createCategory();

        Product product = new Product();
        product.setCategoryId(createdCategoryId);
        product.setName("Cached " + System.nanoTime());
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();
        // 走 create 接口时 ProductServiceImpl 会做这两步；测试直接插库，得自己补。
        // 默认 SKU 尤其不能漏 —— 库存的真相挂在它身上
        bloomFilter.add(createdProductId);
        productSkuService.createDefaultSku(product);
    }

    private void createCategory() {
        if (createdCategoryId != null) {
            return;
        }
        Category category = new Category();
        category.setParentId(0L);
        category.setName("CA" + System.nanoTime());
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();
        createdCategoryIds.add(createdCategoryId);
    }

    @AfterEach
    void cleanup() {
        if (createdProductId != null) {
            redisTemplate.delete(PRODUCT_KEY_PREFIX + createdProductId);
            productMapper.deleteById(createdProductId);
        }
        for (Long id : createdCategoryIds) {
            categoryMapper.deleteById(id);
        }
        createdCategoryIds.clear();
        createdCategoryId = null;
        createdProductId = null;
        redisTemplate.delete(TREE_KEY);
    }
}
