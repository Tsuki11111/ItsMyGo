package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ProductBloomFilter;
import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.ProductSkuView;
import com.tsuk1.itsmygo.dto.StockView;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.ProductSkuService;
import com.tsuk1.itsmygo.service.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    private final ProductSkuService productSkuService;
    private final MerchantMapper merchantMapper;

    public ProductServiceImpl(ProductMapper productMapper, ProductBloomFilter bloomFilter,
                              RedisCacheHelper cacheHelper, StockService stockService,
                              ProductSkuService productSkuService, MerchantMapper merchantMapper) {
        this.productMapper = productMapper;
        this.bloomFilter = bloomFilter;
        this.cacheHelper = cacheHelper;
        this.stockService = stockService;
        this.productSkuService = productSkuService;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public PageResponse<Product> page(int page, int size, Long categoryId, String keyword,
                                      boolean onlyOnSale, Long merchantId, boolean onlyActiveMerchant) {
        Page<Product> pageRequest = new Page<>(normalizePage(page), normalizeSize(size));

        var wrapper = Wrappers.<Product>lambdaQuery()
                .eq(onlyOnSale, Product::getStatus, ON_SALE)
                .eq(merchantId != null, Product::getMerchantId, merchantId)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                // 顾客端只看到 ACTIVE 店铺的商品；管理端不过滤
                .inSql(onlyActiveMerchant, Product::getMerchantId,
                        "SELECT id FROM merchants WHERE status = 'ACTIVE'")
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
                () -> loadDetail(id));
        // 缓存里不含店铺状态：店铺被禁用后即使商品缓存还在，也必须 404
        requireActiveMerchant(product.getMerchantId());
        return withRealtimeStock(product);
    }

    /** 回源：商品本体 + 它的规格与 SKU。进缓存的是数据库里的库存值，展示时才换成实时值 */
    private Product loadDetail(Long id) {
        Product product = requireOnSale(id);
        ProductSkuView view = productSkuService.get(id);
        product.setSpecs(view.specs());
        product.setSkus(view.skus());
        return product;
    }

    /**
     * 把库存换成**实时值**再返回：详情缓存里的库存会滞后（下单不会去失效它，
     * 否则高频下单会把缓存打穿），而用户要知道的是「现在还能买多少」。
     * 每个 SKU 也一并换掉 —— 前端选中某一款时要立刻看到它的余量。
     *
     * 改的是 getOrLoad 出来的副本，不会污染缓存。
     */
    private Product withRealtimeStock(Product product) {
        List<ProductSku> skus = product.getSkus();
        if (skus == null || skus.isEmpty()) {
            product.setStock(0);
            return product;
        }
        Map<Long, Integer> realtime = stockService.currentSkuStocks(
                skus.stream().map(ProductSku::getId).toList());
        int total = 0;
        for (ProductSku sku : skus) {
            int stock = realtime.getOrDefault(sku.getId(), 0);
            sku.setStock(stock);
            total += stock;
        }
        product.setStock(total);
        return product;
    }

    /** 实时库存独立接口：给列表页/轮询用，避免为了一个数字去拉整个详情 */
    @Override
    public StockView stockForCustomer(Long id) {
        requireOnSale(id);
        // skuId 列表从详情缓存取，命中就零查库；库存走批量 multiGet
        Product cached = cacheHelper.getOrLoad(cacheKey(id), PRODUCT_CACHE_TTL, Product.class,
                () -> loadDetail(id));
        requireActiveMerchant(cached.getMerchantId());
        withRealtimeStock(cached);

        List<ProductSku> skus = cached.getSkus() == null ? List.of() : cached.getSkus();
        List<StockView.SkuStockView> skuViews = skus.stream()
                .map(sku -> new StockView.SkuStockView(sku.getId(), sku.getStock(), sku.getStock() > 0))
                .toList();
        return new StockView(id, cached.getStock(), cached.getStock() > 0, skuViews);
    }

    private Product requireOnSale(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    @Override
    @Transactional
    public Product create(ProductRequest request, Long merchantId) {
        requireActiveMerchant(merchantId);
        Product product = new Product();
        product.setMerchantId(merchantId);
        apply(product, request);
        product.setSales(0);
        // 新商品还没有规格，价格与库存先落在它的默认 SKU 上
        product.setHasSpecs(0);
        productMapper.insert(product);
        // ⚠️ 每个商品都必须有 SKU —— 没有的话详情页会显示总库存 0、
        //    下单永远报「库存不足」，看着像库存 bug，其实是数据没铺开
        productSkuService.createDefaultSku(product);
        // 新商品立刻进过滤器，否则它自己的详情页会被判成「不存在」
        bloomFilter.add(product.getId());
        return product;
    }

    @Override
    @Transactional
    public Product update(Long id, ProductRequest request, Long merchantId) {
        Product product = requireOwned(id, merchantId);
        apply(product, request);
        productMapper.updateById(product);
        // 单规格商品的价与库存也挂在它那条默认 SKU 上，得跟着改（里面会一并刷库存计数器）。
        // 配了多规格的商品这里什么都不做 —— 那种要走管理端的分规格接口
        productSkuService.syncDefaultSku(product);
        // Cache-Aside：先更新库，再删缓存。删失败也不要紧，TTL 会兜底
        cacheHelper.evict(cacheKey(id));
        return product;
    }

    @Override
    public void delete(Long id, Long merchantId) {
        requireOwned(id, merchantId);
        productMapper.deleteById(id);
        cacheHelper.evict(cacheKey(id));
    }

    @Override
    public Product detailForMerchant(Long id, Long merchantId) {
        Product product = requireOwned(id, merchantId);
        ProductSkuView view = productSkuService.get(id);
        product.setSpecs(view.specs());
        product.setSkus(view.skus());
        return withRealtimeStock(product);
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

    /** merchantId 为 null 表示平台管理员；非空时商品必须属于该商户 */
    private Product requireOwned(Long id, Long merchantId) {
        Product product = productMapper.selectById(id);
        if (product == null || (merchantId != null && !merchantId.equals(product.getMerchantId()))) {
            // 别人的商品一律当不存在，不泄露存在性
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private void requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantId == null ? null : merchantMapper.selectById(merchantId);
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
