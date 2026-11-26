package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ProductSkuView;
import com.tsuk1.itsmygo.dto.SkuSaveRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.ProductSpec;
import com.tsuk1.itsmygo.entity.ProductSpecValue;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecValueMapper;
import com.tsuk1.itsmygo.service.ProductSkuService;
import com.tsuk1.itsmygo.service.ProductPriceHistoryService;
import com.tsuk1.itsmygo.service.StockService;
import com.tsuk1.itsmygo.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 规格与 SKU。
 *
 * 保存走**整组覆盖**：规格值一改，SKU 组合的集合就变了（加一组「颜色」直接翻倍），
 * 逐个 CRUD 表达不了这件事，所以接口收一张完整的表、服务端算出该增该删该改。
 *
 * **最容易出错的地方是「库存要保住」**：商户改个价格、给某个规格值改个名，
 * 不能把 SKU 删了重建 —— 那样库存和销量全归零。做法是**按规范化后的规格值组合匹配**
 * 到原来那行 SKU，命中就原地更新，只改价格/图片/描述文本，**绝不动 stock**。
 */
@Slf4j
@Service
public class ProductSkuServiceImpl implements ProductSkuService {

    /** 三组已经能盖住绝大多数实物商品（款式 / 颜色 / 尺寸） */
    private static final int MAX_SPECS = 3;
    private static final int MAX_VALUES_PER_SPEC = 20;
    private static final int MAX_SKUS = 100;

    private final ProductMapper productMapper;
    private final ProductSpecMapper specMapper;
    private final ProductSpecValueMapper valueMapper;
    private final ProductSkuMapper skuMapper;
    private final RedisCacheHelper cacheHelper;
    private final StockService stockService;
    private final StorageService storageService;
    private final ProductPriceHistoryService priceHistoryService;

    public ProductSkuServiceImpl(ProductMapper productMapper, ProductSpecMapper specMapper,
                                 ProductSpecValueMapper valueMapper, ProductSkuMapper skuMapper,
                                 RedisCacheHelper cacheHelper, StockService stockService,
                                 StorageService storageService,
                                 ProductPriceHistoryService priceHistoryService) {
        this.productMapper = productMapper;
        this.specMapper = specMapper;
        this.valueMapper = valueMapper;
        this.skuMapper = skuMapper;
        this.cacheHelper = cacheHelper;
        this.stockService = stockService;
        this.storageService = storageService;
        this.priceHistoryService = priceHistoryService;
    }

    // ── 读 ──

    @Override
    public ProductSkuView get(Long productId, Long merchantId) {
        requireOwnedProduct(productId, merchantId);
        List<ProductSpec> specs = loadSpecsWithValues(productId);
        return new ProductSkuView(specs, listSkus(productId));
    }

    // ── 写 ──

    @Override
    @Transactional
    public ProductSkuView save(Long productId, SkuSaveRequest request, Long merchantId) {
        requireOwnedProduct(productId, merchantId);
        validateShape(request);

        // 规格值的新增要先落地拿到 id，SKU 才能引用它们 —— 映射表把负数临时 id 换成真身
        Map<Long, Long> tempIdToRealId = upsertSpecs(productId, request.specs());
        List<Long> removedSkuIds = upsertSkus(productId, request.skus(), tempIdToRealId);

        // SKU 变了，商品的汇总字段（最低价 / 总库存 / 有没有规格）跟着重算
        productMapper.recalcFromSkus(productId);
        // 详情缓存里带着旧的 SKU 列表，必须失效
        cacheHelper.evict(RedisCacheHelper.key("product:" + productId));
        // **三处库存口径要一起刷**：SKU 行（上面已写库）、商品汇总（上一行已重算）、Redis 键（这里）。
        // 漏掉键的话，顾客看到的还是改动前的库存。
        for (ProductSku sku : listSkus(productId)) {
            stockService.reload(sku.getId());
        }
        // 被删掉的 SKU 把键也清掉 —— 留着就是一条再也读不到的脏数据
        for (Long removedSkuId : removedSkuIds) {
            stockService.drop(removedSkuId);
        }

        return get(productId);
    }

    @Override
    public void createDefaultSku(Product product) {
        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSpecValueIds("");
        sku.setSpecText("");
        sku.setPrice(product.getPrice());
        sku.setStock(product.getStock());
        sku.setSales(0);
        sku.setImage(nullToEmpty(product.getMainImage()));
        sku.setThumbImage(nullToEmpty(product.getThumbImage()));
        sku.setStatus(1);
        skuMapper.insert(sku);
    }

    @Override
    @Transactional
    public ProductSku adjustStock(Long productId, Long skuId, Integer stock, Integer expectedStock,
                                  Long merchantId) {
        requireOwnedProduct(productId, merchantId);
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            // 别人商品下的 SKU 一律当「不存在」，不泄露存在性
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }

        int updated = skuMapper.compareAndSetStock(productId, skuId, stock, expectedStock);
        if (updated == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存已变化，请刷新后重试");
        }

        // 库存变了，商品汇总、详情缓存、Redis 计数器三处都要跟着动
        productMapper.recalcFromSkus(productId);
        cacheHelper.evict(RedisCacheHelper.key("product:" + productId));
        stockService.reload(skuId);
        return skuMapper.selectById(skuId);
    }

    @Override
    public void syncDefaultSku(Product product) {
        ProductSku sku = findDefaultSku(product.getId());
        if (sku == null) {
            // 要么这是多规格商品（那不该由商品级接口改价），要么数据没铺开 —— 都不动
            return;
        }
        sku.setPrice(product.getPrice());
        sku.setStock(product.getStock());
        sku.setImage(nullToEmpty(product.getMainImage()));
        sku.setThumbImage(nullToEmpty(product.getThumbImage()));
        skuMapper.updateById(sku);
        // 库存计数器是**独立的一份状态**，粒度又是 SKU —— 不刷的话顾客看到的还是旧数字。
        // ⚠️ 这里必须用 sku.getId()，传商品 id 过去查到的是另一条 SKU（或者什么都查不到）。
        stockService.reload(sku.getId());
    }

    // ── 规格与规格值的整组覆盖 ──

    /**
     * 规格有 id 就改、没 id 就建，请求里没出现的连值一起删。
     *
     * @return 负数临时 id → 真实 id 的映射（新建的规格值），供 SKU 改写引用
     */
    private Map<Long, Long> upsertSpecs(Long productId, List<SkuSaveRequest.SpecPayload> payloads) {
        Map<Long, Long> tempIdToRealId = new HashMap<>();

        List<ProductSpec> existing = specMapper.selectList(
                Wrappers.<ProductSpec>lambdaQuery().eq(ProductSpec::getProductId, productId));
        // 每个规格下已有的值 id 集合，后面判断「哪些值被删了」
        Map<Long, Set<Long>> valueIdsBySpec = new HashMap<>();
        if (!existing.isEmpty()) {
            List<Long> existingSpecIds = new ArrayList<>(existing.size());
            for (ProductSpec spec : existing) {
                existingSpecIds.add(spec.getId());
            }
            List<ProductSpecValue> existingValues = valueMapper.selectList(
                    Wrappers.<ProductSpecValue>lambdaQuery()
                            .in(ProductSpecValue::getSpecId, existingSpecIds));
            for (ProductSpecValue value : existingValues) {
                Set<Long> ids = valueIdsBySpec.get(value.getSpecId());
                if (ids == null) {
                    ids = new HashSet<>();
                    valueIdsBySpec.put(value.getSpecId(), ids);
                }
                ids.add(value.getId());
            }
        }

        Set<Long> keepSpecIds = new HashSet<>();
        for (SkuSaveRequest.SpecPayload payload : payloads) {
            if (payload.id() != null) {
                keepSpecIds.add(payload.id());
            }
        }

        for (ProductSpec old : existing) {
            if (!keepSpecIds.contains(old.getId())) {
                valueMapper.delete(Wrappers.<ProductSpecValue>lambdaQuery()
                        .eq(ProductSpecValue::getSpecId, old.getId()));
                specMapper.deleteById(old.getId());
            }
        }

        for (int i = 0; i < payloads.size(); i++) {
            SkuSaveRequest.SpecPayload payload = payloads.get(i);
            ProductSpec spec = new ProductSpec();
            spec.setProductId(productId);
            spec.setName(payload.name());
            spec.setSort(i);

            Set<Long> knownValueIds;
            if (payload.id() != null) {
                if (!valueIdsBySpec.containsKey(payload.id())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "规格不属于该商品");
                }
                spec.setId(payload.id());
                specMapper.updateById(spec);
                knownValueIds = valueIdsBySpec.get(payload.id());
            } else {
                specMapper.insert(spec);
                knownValueIds = Set.of();
            }

            Set<Long> keepValueIds = new HashSet<>();
            for (SkuSaveRequest.ValuePayload valuePayload : payload.values()) {
                Long id = valuePayload.id();
                if (id != null && id > 0) {
                    keepValueIds.add(id);
                }
            }
            for (Long oldValueId : knownValueIds) {
                if (!keepValueIds.contains(oldValueId)) {
                    valueMapper.deleteById(oldValueId);
                }
            }

            for (int j = 0; j < payload.values().size(); j++) {
                SkuSaveRequest.ValuePayload valuePayload = payload.values().get(j);
                ProductSpecValue value = new ProductSpecValue();
                value.setSpecId(spec.getId());
                value.setValue(valuePayload.value());
                value.setSort(j);

                Long id = valuePayload.id();
                if (id != null && id > 0) {
                    if (!knownValueIds.contains(id)) {
                        throw new BizException(ResultCode.BAD_REQUEST, "规格值不属于该商品的规格");
                    }
                    value.setId(id);
                    valueMapper.updateById(value);
                } else {
                    valueMapper.insert(value);
                    tempIdToRealId.put(id, value.getId());
                }
            }
        }
        return tempIdToRealId;
    }

    // ── SKU 的整组覆盖 ──

    /** @return 本次被删掉的 SKU id，调用方要拿它去清 Redis 键 */
    private List<Long> upsertSkus(Long productId, List<SkuSaveRequest.SkuPayload> payloads,
                                  Map<Long, Long> tempIdToRealId) {
        SpecMeta meta = loadSpecMeta(productId);

        Map<String, SkuSaveRequest.SkuPayload> payloadByKey = new LinkedHashMap<>();
        Map<String, List<Long>> idsByKey = new HashMap<>();

        for (SkuSaveRequest.SkuPayload payload : payloads) {
            List<Long> ids = new ArrayList<>(payload.specValueIds().size());
            for (Long raw : payload.specValueIds()) {
                Long real = (raw != null && raw > 0) ? raw : tempIdToRealId.get(raw);
                if (real == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "SKU 引用了不存在的规格值");
                }
                ids.add(real);
            }

            if (ids.size() != meta.specs().size()) {
                throw new BizException(ResultCode.BAD_REQUEST, "每个 SKU 要为每组规格各选一个值");
            }
            Set<Long> distinctIds = new HashSet<>(ids);
            if (distinctIds.size() != ids.size()) {
                throw new BizException(ResultCode.BAD_REQUEST, "同一个 SKU 里出现了重复的规格值");
            }
            for (Long id : ids) {
                if (!meta.valueById().containsKey(id)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "规格值不属于该商品");
                }
            }

            String key = normalize(ids);
            if (payloadByKey.putIfAbsent(key, payload) != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "存在重复的规格组合");
            }
            idsByKey.put(key, ids);
        }

        Map<String, ProductSku> existingByKey = new HashMap<>();
        for (ProductSku sku : listSkus(productId)) {
            existingByKey.put(sku.getSpecValueIds(), sku);
        }

        List<Long> removed = new ArrayList<>();
        for (ProductSku old : existingByKey.values()) {
            if (!payloadByKey.containsKey(old.getSpecValueIds())) {
                skuMapper.deleteById(old.getId());
                removed.add(old.getId());
            }
        }

        for (Map.Entry<String, SkuSaveRequest.SkuPayload> entry : payloadByKey.entrySet()) {
            String key = entry.getKey();
            SkuSaveRequest.SkuPayload payload = entry.getValue();
            ProductSku existing = existingByKey.get(key);
            ProductSku sku = existing != null ? existing : new ProductSku();
            BigDecimal previousPrice = existing == null ? null : existing.getPrice();

            sku.setProductId(productId);
            sku.setSpecValueIds(key);
            sku.setSpecText(buildSpecText(idsByKey.get(key), meta));
            sku.setPrice(payload.price());
            String newImage = nullToEmpty(payload.image());
            sku.setImage(newImage);
            // 图片换过、或者老数据还没缩略图时才生成；避免每次改价都重跑一遍
            if (existing == null
                    || !Objects.equals(existing.getImage(), newImage)
                    || !StringUtils.hasText(existing.getThumbImage())) {
                sku.setThumbImage(storageService.ensureProductThumbnail(newImage));
            }

            if (existing == null) {
                sku.setStock(payload.stock());
                sku.setSales(0);
                sku.setStatus(1);
                skuMapper.insert(sku);
            } else {
                // ⚠️ 命中已有的 SKU 时，**stock / sales / status 一个都不碰** ——
                //    库存是下单和取消改的，商户改个价格不该把它冲掉
                skuMapper.updateById(sku);
            }
            // 规格价格真的变了才记历史（sku_id 有值，product_id 也填）——
            // 智能推广的「降价」信号需要它
            priceHistoryService.recordIfChanged(productId, sku.getId(), previousPrice, sku.getPrice());
        }
        return removed;
    }

    // ── 校验 ──

    private void validateShape(SkuSaveRequest request) {
        if (request.specs().size() > MAX_SPECS) {
            throw new BizException(ResultCode.BAD_REQUEST, "规格最多 " + MAX_SPECS + " 组");
        }
        for (SkuSaveRequest.SpecPayload spec : request.specs()) {
            if (spec.values().size() > MAX_VALUES_PER_SPEC) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "每组规格最多 " + MAX_VALUES_PER_SPEC + " 个值");
            }
        }
        if (request.skus().size() > MAX_SKUS) {
            throw new BizException(ResultCode.BAD_REQUEST, "SKU 最多 " + MAX_SKUS + " 条");
        }
        if (request.specs().isEmpty() && request.skus().size() > 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有规格的商品只能有一个 SKU");
        }
    }

    // ── 组装 ──

    /** 商品的规格元信息：规格（带值）本身，以及「规格值 → 规格」的归属关系 */
    private record SpecMeta(List<ProductSpec> specs,
                            Map<Long, ProductSpecValue> valueById,
                            Map<Long, Long> specIdByValueId) {
    }

    private SpecMeta loadSpecMeta(Long productId) {
        List<ProductSpec> specs = loadSpecsWithValues(productId);
        Map<Long, ProductSpecValue> valueById = new LinkedHashMap<>();
        Map<Long, Long> specIdByValueId = new HashMap<>();
        for (ProductSpec spec : specs) {
            for (ProductSpecValue value : spec.getValues()) {
                valueById.put(value.getId(), value);
                specIdByValueId.put(value.getId(), spec.getId());
            }
        }
        return new SpecMeta(specs, valueById, specIdByValueId);
    }

    private List<ProductSpec> loadSpecsWithValues(Long productId) {
        List<ProductSpec> specs = specMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                .eq(ProductSpec::getProductId, productId)
                .orderByAsc(ProductSpec::getSort));
        if (specs.isEmpty()) {
            return specs;
        }

        List<Long> specIds = new ArrayList<>(specs.size());
        for (ProductSpec spec : specs) {
            specIds.add(spec.getId());
        }
        List<ProductSpecValue> values = valueMapper.selectList(
                Wrappers.<ProductSpecValue>lambdaQuery()
                        .in(ProductSpecValue::getSpecId, specIds)
                        .orderByAsc(ProductSpecValue::getSort));

        // 按规格 id 分组，再挂回每个规格
        Map<Long, List<ProductSpecValue>> valuesBySpec = new HashMap<>();
        for (ProductSpecValue value : values) {
            List<ProductSpecValue> list = valuesBySpec.get(value.getSpecId());
            if (list == null) {
                list = new ArrayList<>();
                valuesBySpec.put(value.getSpecId(), list);
            }
            list.add(value);
        }
        for (ProductSpec spec : specs) {
            spec.setValues(valuesBySpec.getOrDefault(spec.getId(), List.of()));
        }
        return specs;
    }

    /**
     * 「款式:云宝;颜色:白」—— 这段文本会被快照进订单。
     * 按规格顺序拼，而不是按 id 顺序 —— 后者是 id 升序，跟商户看到的顺序无关。
     */
    private String buildSpecText(List<Long> valueIds, SpecMeta meta) {
        if (meta.specs().isEmpty()) {
            return "";
        }
        // 规格值 id → 它所属的规格；用来按规格顺序拼文本
        Map<Long, ProductSpec> specByValueId = new HashMap<>();
        for (ProductSpec spec : meta.specs()) {
            for (ProductSpecValue value : spec.getValues()) {
                specByValueId.put(value.getId(), spec);
            }
        }
        // 按规格顺序拼「规格名:值」，而不是按 id 顺序
        List<String> parts = new ArrayList<>();
        for (ProductSpec spec : meta.specs()) {
            for (Long valueId : valueIds) {
                ProductSpec valueSpec = specByValueId.get(valueId);
                if (valueSpec == null || !valueSpec.getId().equals(spec.getId())) {
                    continue;
                }
                ProductSpecValue value = meta.valueById().get(valueId);
                if (value != null) {
                    parts.add(spec.getName() + ":" + value.getValue());
                }
                // 一个规格只需要一个值，找到就处理下一组规格
                break;
            }
        }
        return String.join(";", parts);
    }

    /** 升序 + 去重后的规格值 id 串 —— 规格组合的规范键，唯一索引和 SKU 匹配都靠它 */
    private String normalize(List<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<Long> sorted = new ArrayList<>(distinct);
        sorted.sort(Long::compareTo);
        List<String> parts = new ArrayList<>(sorted.size());
        for (Long id : sorted) {
            parts.add(String.valueOf(id));
        }
        return String.join(",", parts);
    }

    // ── 小工具 ──

    private Product requireOwnedProduct(Long productId, Long merchantId) {
        Product product = productMapper.selectById(productId);
        if (product == null || (merchantId != null && !merchantId.equals(product.getMerchantId()))) {
            // 别人的商品一律当不存在，不泄露存在性
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private List<ProductSku> listSkus(Long productId) {
        return skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    private ProductSku findDefaultSku(Long productId) {
        return skuMapper.selectOne(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getSpecValueIds, ""));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
