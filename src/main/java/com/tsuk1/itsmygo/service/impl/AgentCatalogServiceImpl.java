package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.ProductReviewStat;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                true, null, true).records();
        if (products.isEmpty()) {
            return List.of();
        }
        return enrich(products).stream()
                .filter(view -> priceMin == null
                        || moneyValue(view.price()).compareTo(priceMin) >= 0)
                .filter(view -> priceMax == null
                        || moneyValue(view.price()).compareTo(priceMax) <= 0)
                .sorted(comparator(sort))
                .limit(limit)
                .toList();
    }

    @Override
    public List<AgentProductView> listAll(int limit) {
        int safeLimit = clamp(limit, 1, 100);
        List<Product> products = productService.page(1, safeLimit, null, null,
                true, null, true).records();
        return enrich(products);
    }

    @Override
    public List<CategoryNode> categories() {
        return categoryService.tree(true);
    }

    @Override
    public List<AgentAddressView> addresses(Long userId) {
        return addressService.list(userId).stream()
                .map(address -> new AgentAddressView(
                        address.getId(),
                        nullToEmpty(address.getReceiver()),
                        nullToEmpty(address.getPhone()),
                        fullAddress(address),
                        address.getIsDefault() != null && address.getIsDefault() == 1))
                .toList();
    }

    /** 批量回填评价 / 店铺 / 分类，避免按商品 N+1 */
    private List<AgentProductView> enrich(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, ProductReviewStat> stats = reviewMapper.statsByProductIds(ids).stream()
                .collect(Collectors.toMap(ProductReviewStat::getProductId, Function.identity()));

        List<Long> merchantIds = products.stream().map(Product::getMerchantId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Merchant> merchants = merchantIds.isEmpty() ? Map.of()
                : merchantMapper.selectByIds(merchantIds).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));

        List<Long> categoryIds = products.stream().map(Product::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Category> categories = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return products.stream()
                .map(product -> toProductView(product, merchants, categories, stats))
                .filter(Objects::nonNull)
                .toList();
    }

    private AgentProductView toProductView(Product product,
                                           Map<Long, Merchant> merchants,
                                           Map<Long, Category> categories,
                                           Map<Long, ProductReviewStat> stats) {
        try {
            Product detail = productService.detailForCustomer(product.getId());
            List<ProductSku> rawSkus = detail.getSkus() == null ? List.of()
                    : detail.getSkus().stream()
                    .filter(sku -> sku.getStatus() == null || sku.getStatus() == 1)
                    .toList();
            List<AgentProductView.Sku> skus = rawSkus.stream()
                    .map(sku -> new AgentProductView.Sku(
                            sku.getId(),
                            nullToEmpty(sku.getSpecText()),
                            money(sku.getPrice()),
                            sku.getStock()))
                    .toList();
            int totalStock = rawSkus.stream()
                    .mapToInt(sku -> sku.getStock() == null ? 0 : sku.getStock()).sum();
            BigDecimal minPrice = rawSkus.stream().map(ProductSku::getPrice)
                    .filter(Objects::nonNull).min(BigDecimal::compareTo)
                    .orElse(detail.getPrice());

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

    private Comparator<AgentProductView> comparator(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "price_asc" -> Comparator.comparing(
                    (AgentProductView view) -> moneyValue(view.price()));
            case "price_desc" -> Comparator.comparing(
                    (AgentProductView view) -> moneyValue(view.price())).reversed();
            case "newest" -> Comparator.comparing(AgentProductView::productId).reversed();
            case "sales" -> Comparator.comparingInt(
                            (AgentProductView view) -> sales(view)).reversed()
                    .thenComparing(Comparator.comparing(AgentProductView::productId).reversed());
            default -> Comparator
                    .comparingInt((AgentProductView view) ->
                            view.totalStock() != null && view.totalStock() > 0 ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(
                            (AgentProductView view) -> sales(view)).reversed())
                    .thenComparing(Comparator.comparingDouble(
                            (AgentProductView view) -> rating(view)).reversed())
                    .thenComparing(Comparator.comparing(AgentProductView::productId).reversed());
        };
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
