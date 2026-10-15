package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ProductBloomFilter;
import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.StockView;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.StockService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int ON_SALE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    /** 商品详情缓存时长；更新商品时主动删除，这个 TTL 只是删失败时的兜底 */
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofMinutes(10);

    private final ProductMapper productMapper;
    private final ProductBloomFilter bloomFilter;
    private final RedisCacheHelper cacheHelper;
    private final StockService stockService;

    public ProductServiceImpl(ProductMapper productMapper, ProductBloomFilter bloomFilter,
                              RedisCacheHelper cacheHelper, StockService stockService) {
        this.productMapper = productMapper;
        this.bloomFilter = bloomFilter;
        this.cacheHelper = cacheHelper;
        this.stockService = stockService;
    }

    @Override
    public PageResponse<Product> page(int page, int size, Long categoryId, String keyword, boolean onlyOnSale) {
        Page<Product> pageRequest = new Page<>(normalizePage(page), normalizeSize(size));

        var wrapper = Wrappers.<Product>lambdaQuery()
                .eq(onlyOnSale, Product::getStatus, ON_SALE)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                // 关键词目前用 LIKE；数据量上万后再考虑 FULLTEXT(ngram) 或搜索引擎
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or().like(Product::getSubtitle, keyword))
                .orderByDesc(Product::getId);

        return PageResponse.of(productMapper.selectPage(pageRequest, wrapper));
    }

    /**
     * 顾客端详情 —— 三层：
     *   ① 布隆过滤器挡掉「一定不存在」的 ID（防穿透，一次库都不查）
     *   ② Redis 缓存命中直接返回（带互斥锁回源，防击穿）
     *   ③ 都没命中才查库，并写回缓存
     *
     * 出来前把 stock 换成**实时值**：详情缓存里的库存会滞后（下单不会去失效它，
     * 否则高频下单会把缓存打穿），而用户要知道的是「现在还能买多少」。
     * 实时值来自 Redis 计数器，同样不查库。
     */
    @Override
    public Product detailForCustomer(Long id) {
        if (!bloomFilter.mightContain(id)) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        Product product = cacheHelper.getOrLoad(cacheKey(id), PRODUCT_CACHE_TTL, Product.class,
                () -> requireOnSale(id));
        // getOrLoad 出来的对象是反序列化的新实例，改它不会污染缓存
        product.setStock(stockService.currentStock(id));
        return product;
    }

    /** 实时库存独立接口：给列表页/轮询用，避免为了一个数字去拉整个详情 */
    @Override
    public StockView stockForCustomer(Long id) {
        requireOnSale(id);
        int stock = stockService.currentStock(id);
        return new StockView(id, stock, stock > 0);
    }

    private Product requireOnSale(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    @Override
    public Product create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setSales(0);
        productMapper.insert(product);
        // 新商品立刻进过滤器，否则它自己的详情页会被判成「不存在」
        bloomFilter.add(product.getId());
        return product;
    }

    @Override
    public Product update(Long id, ProductRequest request) {
        Product product = requireExists(id);
        apply(product, request);
        productMapper.updateById(product);
        // Cache-Aside：先更新库，再删缓存。删失败也不要紧，TTL 会兜底
        cacheHelper.evict(cacheKey(id));
        // 库存计数器是独立的一份状态：管理员改了 stock 必须同步过去，
        // 否则顾客看到的仍是旧库存（下单不会超卖，但显示是错的）
        stockService.reload(id);
        return product;
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        productMapper.deleteById(id);
        cacheHelper.evict(cacheKey(id));
    }

    private String cacheKey(Long id) {
        return RedisCacheHelper.key("product:" + id);
    }

    private void apply(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setSubtitle(request.subtitle() == null ? "" : request.subtitle());
        product.setMainImage(request.mainImage() == null ? "" : request.mainImage());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setDetail(request.detail());
        product.setStatus(Boolean.TRUE.equals(request.onSale()) ? ON_SALE : 0);
    }

    private Product requireExists(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
