package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.ProductReviewStat;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentAddressEditRequest;
import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentProductSearchRequest;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.service.AddressService;
import com.tsuk1.itsmygo.service.AgentCatalogService;
import com.tsuk1.itsmygo.service.CategoryService;
import com.tsuk1.itsmygo.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * agent 的只读数据入口：商品目录（筛选 / 排序 / 评分 / 库存）与当前用户的收货地址。
 *
 * <p>商品量小（演示目录几十条），推荐排序在内存里做；量大了应换成搜索引擎 /
 * 语义检索，而不是让模型写 SQL。
 */
@Service
public class AgentCatalogServiceImpl implements AgentCatalogService {

    private final ProductService productService;
    private final AddressService addressService;
    private final CategoryService categoryService;
    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;
    private final CategoryMapper categoryMapper;

    public AgentCatalogServiceImpl(ProductService productService,
                                   AddressService addressService,
                                   CategoryService categoryService,
                                   ReviewMapper reviewMapper,
                                   MerchantMapper merchantMapper,
                                   CategoryMapper categoryMapper) {
        this.productService = productService;
        this.addressService = addressService;
        this.categoryService = categoryService;
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<AgentProductView> searchProducts(AgentProductSearchRequest request) {
        int limit = clamp(request == null || request.limit() == null ? 5 : request.limit(), 1, 10);
        String keyword = request == null ? null : request.keyword();
        Long categoryId = request == null ? null : request.categoryId();
        BigDecimal priceMin = request == null ? null : request.priceMin();
        BigDecimal priceMax = request == null ? null : request.priceMax();
        String sort = request == null ? "recommend" : request.sort();

        // 多取一些候选，价格区间与推荐排序在内存里做（演示目录量级足够）
        List<Product> products = productService.page(1, 50, categoryId, keyword,
                null, true, null, true).records();
        if (products.isEmpty()) {
            return List.of();
        }
        List<AgentProductView> views = enrich(products);
        // 价格区间在内存里过滤（演示目录只有几十条，不必写进 SQL）
        List<AgentProductView> filtered = new ArrayList<>();
        for (AgentProductView view : views) {
            if (priceMin != null && moneyValue(view.price()).compareTo(priceMin) < 0) {
                continue;
            }
            if (priceMax != null && moneyValue(view.price()).compareTo(priceMax) > 0) {
                continue;
            }
            filtered.add(view);
        }
        filtered.sort(comparator(sort));
        if (filtered.size() > limit) {
            return new ArrayList<>(filtered.subList(0, limit));
        }
        return filtered;
    }

    @Override
    public List<AgentProductView> listAll(int limit) {
        int safeLimit = clamp(limit, 1, 100);
        List<Product> products = productService.page(1, safeLimit, null, null,
                null, true, null, true).records();
        return enrich(products);
    }

    @Override
    public List<CategoryNode> categories() {
        return categoryService.tree(true);
    }

    @Override
    public List<AgentAddressView> addresses(Long userId) {
        List<Address> addresses = addressService.list(userId);
        List<AgentAddressView> views = new ArrayList<>(addresses.size());
        for (Address address : addresses) {
            views.add(new AgentAddressView(
                    address.getId(),
                    nullToEmpty(address.getReceiver()),
                    nullToEmpty(address.getPhone()),
                    fullAddress(address),
                    address.getIsDefault() != null && address.getIsDefault() == 1));
        }
        return views;
    }

    @Override
    public AgentActionView updateAddress(long userId, AgentAddressEditRequest request) {
        // 复用 PUT /api/addresses/{id} 的更新逻辑：归属校验（越权按不存在返回 404）
        // 与字段校验都在 AddressService 内完成；isDefault 传 null 表示保留原默认标记。
        Address address = addressService.update(userId, request.addressId(),
                new AddressRequest(request.receiver(), request.phone(), request.province(),
                        request.city(), request.district(), request.detail(), null));

        Map<String, Object> addressView = new LinkedHashMap<>();
        addressView.put("id", address.getId());
        addressView.put("receiver", nullToEmpty(address.getReceiver()));
        addressView.put("address", fullAddress(address));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("address", addressView);
        summary.put("note", "收货地址已更新。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addressId", address.getId());
        result.put("note", "地址修改已保存。");

        // 地址修改是即时生效的，没有待确认动作；actionNo 留空，summary.address 供 agent 读取
        return new AgentActionView("", "ADDRESS_EDIT", "SUCCEEDED", summary, result, "",
                null, LocalDateTime.now());
    }

    /** 批量回填评价 / 店铺 / 分类，避免按商品 N+1 */
    private List<AgentProductView> enrich(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(products.size());
        Set<Long> merchantIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (Product product : products) {
            ids.add(product.getId());
            if (product.getMerchantId() != null) {
                merchantIds.add(product.getMerchantId());
            }
            if (product.getCategoryId() != null) {
                categoryIds.add(product.getCategoryId());
            }
        }

        // 评价统计：一次查回这一批商品
        Map<Long, ProductReviewStat> stats = new HashMap<>();
        for (ProductReviewStat stat : reviewMapper.statsByProductIds(ids)) {
            stats.put(stat.getProductId(), stat);
        }
        Map<Long, Merchant> merchants = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            for (Merchant merchant : merchantMapper.selectByIds(merchantIds)) {
                merchants.put(merchant.getId(), merchant);
            }
        }
        Map<Long, Category> categories = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (Category category : categoryMapper.selectByIds(categoryIds)) {
                categories.put(category.getId(), category);
            }
        }

        List<AgentProductView> views = new ArrayList<>(products.size());
        for (Product product : products) {
            AgentProductView view = toProductView(product, merchants, categories, stats);
            if (view != null) {
                views.add(view);
            }
        }
        return views;
    }

    private AgentProductView toProductView(Product product,
                                           Map<Long, Merchant> merchants,
                                           Map<Long, Category> categories,
                                           Map<Long, ProductReviewStat> stats) {
        try {
            Product detail = productService.detailForCustomer(product.getId());
            // 只保留在售 SKU
            List<ProductSku> rawSkus = new ArrayList<>();
            if (detail.getSkus() != null) {
                for (ProductSku sku : detail.getSkus()) {
                    if (sku.getStatus() == null || sku.getStatus() == 1) {
                        rawSkus.add(sku);
                    }
                }
            }
            // 转成 agent 用的精简 SKU 视图，同时累计总库存、找最低价
            List<AgentProductView.Sku> skus = new ArrayList<>(rawSkus.size());
            int totalStock = 0;
            BigDecimal minPrice = null;
            for (ProductSku sku : rawSkus) {
                skus.add(new AgentProductView.Sku(
                        sku.getId(),
                        nullToEmpty(sku.getSpecText()),
                        money(sku.getPrice()),
                        sku.getStock()));
                totalStock += sku.getStock() == null ? 0 : sku.getStock();
                if (sku.getPrice() != null
                        && (minPrice == null || sku.getPrice().compareTo(minPrice) < 0)) {
                    minPrice = sku.getPrice();
                }
            }
            if (minPrice == null) {
                minPrice = detail.getPrice();
            }

            ProductReviewStat stat = stats.get(detail.getId());
            long reviewCount = stat == null || stat.getReviewCount() == null
                    ? 0 : stat.getReviewCount();
            String rating = reviewCount > 0 && stat.getAverageRating() != null
                    ? String.format(Locale.ROOT, "%.1f", stat.getAverageRating()) : "";

            Merchant merchant = merchants.get(detail.getMerchantId());
            Category category = categories.get(detail.getCategoryId());
            return new AgentProductView(
                    detail.getId(),
                    nullToEmpty(detail.getName()),
                    nullToEmpty(detail.getSubtitle()),
                    plain(detail.getDetail()),
                    nullToEmpty(detail.getMainImage()),
                    money(minPrice),
                    totalStock,
                    detail.getSales() == null ? 0 : detail.getSales(),
                    rating,
                    reviewCount,
                    merchant == null ? "" : nullToEmpty(merchant.getName()),
                    category == null ? "" : nullToEmpty(category.getName()),
                    detail.getHasSpecs() != null && detail.getHasSpecs() == 1,
                    skus);
        } catch (BizException e) {
            // 分页到详情之间被下架 / 删除：跳过这一条，不要让整个搜索失败
            return null;
        }
    }

    /**
     * 返回「按什么排序」的比较器。
     * Comparator 是 Java 里表示「两个对象谁排在前面」的接口：
     * comparing(...) 指定排序字段，reversed() 表示倒序，
     * thenComparing(...) 是前一个字段相等时的次级排序规则。
     */
    private Comparator<AgentProductView> comparator(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<AgentProductView> byPrice = Comparator.comparing(
                (AgentProductView view) -> moneyValue(view.price()));
        Comparator<AgentProductView> byNewest = Comparator.comparing(
                (AgentProductView view) -> view.productId()).reversed();
        Comparator<AgentProductView> bySales = Comparator.comparingInt(
                (AgentProductView view) -> sales(view)).reversed();

        switch (key) {
            case "price_asc":
                return byPrice;
            case "price_desc":
                return byPrice.reversed();
            case "newest":
                return byNewest;
            case "sales":
                return bySales.thenComparing(byNewest);
            default:
                // 推荐排序：有库存的排前面，其次销量高，其次评分高，最后新的商品优先
                Comparator<AgentProductView> inStockFirst = Comparator.comparingInt(
                        (AgentProductView view) ->
                                view.totalStock() != null && view.totalStock() > 0 ? 0 : 1);
                Comparator<AgentProductView> byRating = Comparator.comparingDouble(
                        (AgentProductView view) -> rating(view)).reversed();
                return inStockFirst.thenComparing(bySales).thenComparing(byRating)
                        .thenComparing(byNewest);
        }
    }

    private int sales(AgentProductView view) {
        return view.sales() == null ? 0 : view.sales();
    }

    private double rating(AgentProductView view) {
        try {
            return StringUtils.hasText(view.rating()) ? Double.parseDouble(view.rating()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal moneyValue(String value) {
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String plain(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String fullAddress(Address address) {
        return nullToEmpty(address.getProvince()) + nullToEmpty(address.getCity())
                + nullToEmpty(address.getDistrict()) + nullToEmpty(address.getDetail());
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
