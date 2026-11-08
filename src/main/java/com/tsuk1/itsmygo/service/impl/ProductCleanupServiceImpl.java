package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.product.ProductCleanupProperties;
import com.tsuk1.itsmygo.dto.ProductCleanupReport;
import com.tsuk1.itsmygo.entity.AfterSaleItem;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import com.tsuk1.itsmygo.entity.ProductQuestion;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.ProductSpec;
import com.tsuk1.itsmygo.entity.ProductSpecValue;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.UserFavorite;
import com.tsuk1.itsmygo.entity.UserFootprint;
import com.tsuk1.itsmygo.mapper.AfterSaleItemMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductPriceHistoryMapper;
import com.tsuk1.itsmygo.mapper.ProductQuestionMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecValueMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserFavoriteMapper;
import com.tsuk1.itsmygo.mapper.UserFootprintMapper;
import com.tsuk1.itsmygo.service.NotificationService;
import com.tsuk1.itsmygo.service.ProductCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期下架商品处理：
 * 1. 下架超过 offShelfDays → 归档（archived_at，可恢复）；
 * 2. 归档超过 archiveGraceDays 且**无任何引用**（订单/售后/购物车/评价/问答/收藏/足迹/价格历史）
 *    → 物理删除商品 + SKU + 规格；图片不删（避免误伤订单快照，后续可做对象存储清理任务）。
 */
@Slf4j
@Service
public class ProductCleanupServiceImpl implements ProductCleanupService {

    private static final int OFF_SHELF = 0;
    private static final String CACHE_KEY_PREFIX = "product:";

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductSpecValueMapper productSpecValueMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderItemMapper orderItemMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final ReviewMapper reviewMapper;
    private final ProductQuestionMapper productQuestionMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserFootprintMapper userFootprintMapper;
    private final ProductPriceHistoryMapper priceHistoryMapper;
    private final RedisCacheHelper cacheHelper;
    private final NotificationService notificationService;
    private final ProductCleanupProperties properties;

    public ProductCleanupServiceImpl(ProductMapper productMapper,
                                     ProductSkuMapper productSkuMapper,
                                     ProductSpecMapper productSpecMapper,
                                     ProductSpecValueMapper productSpecValueMapper,
                                     CartItemMapper cartItemMapper,
                                     OrderItemMapper orderItemMapper,
                                     AfterSaleItemMapper afterSaleItemMapper,
                                     ReviewMapper reviewMapper,
                                     ProductQuestionMapper productQuestionMapper,
                                     UserFavoriteMapper userFavoriteMapper,
                                     UserFootprintMapper userFootprintMapper,
                                     ProductPriceHistoryMapper priceHistoryMapper,
                                     RedisCacheHelper cacheHelper,
                                     NotificationService notificationService,
                                     ProductCleanupProperties properties) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.productSpecMapper = productSpecMapper;
        this.productSpecValueMapper = productSpecValueMapper;
        this.cartItemMapper = cartItemMapper;
        this.orderItemMapper = orderItemMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.reviewMapper = reviewMapper;
        this.productQuestionMapper = productQuestionMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userFootprintMapper = userFootprintMapper;
        this.priceHistoryMapper = priceHistoryMapper;
        this.cacheHelper = cacheHelper;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public ProductCleanupReport run(boolean dryRun) {
        LocalDateTime now = LocalDateTime.now();
        int batch = Math.max(1, Math.min(properties.getBatchLimit(), 500));

        List<Product> archiveCandidates = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, OFF_SHELF)
                .isNull(Product::getArchivedAt)
                .le(Product::getUpdatedAt, now.minusDays(properties.getOffShelfDays()))
                .orderByAsc(Product::getId)
                .last("LIMIT " + batch));

        int archived = 0;
        if (!dryRun) {
            for (Product product : archiveCandidates) {
                int rows = productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                        .eq(Product::getId, product.getId())
                        .eq(Product::getStatus, OFF_SHELF)
                        .isNull(Product::getArchivedAt)
                        .set(Product::getArchivedAt, now));
                if (rows > 0) {
                    archived++;
                    // 归档后清购物车里的失效行（不进订单，无历史价值）
                    cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                            .eq(CartItem::getProductId, product.getId()));
                    cacheHelper.evict(RedisCacheHelper.key(CACHE_KEY_PREFIX + product.getId()));
                }
            }
        }

        List<Product> purgeCandidates = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, OFF_SHELF)
                .isNotNull(Product::getArchivedAt)
                .le(Product::getArchivedAt, now.minusDays(properties.getArchiveGraceDays()))
                .orderByAsc(Product::getId)
                .last("LIMIT " + batch));

        int purgeable = 0;
        int purged = 0;
        int skipped = 0;
        for (Product product : purgeCandidates) {
            if (hasReferences(product.getId())) {
                skipped++;
                continue;
            }
            purgeable++;
            if (!dryRun) {
                purge(product.getId());
                purged++;
            }
        }

        ProductCleanupReport report = new ProductCleanupReport(
                dryRun, archiveCandidates.size(), dryRun ? 0 : archived,
                purgeable, dryRun ? 0 : purged, skipped);
        log.info("商品清理完成：dryRun={} 待归档={} 已归档={} 可清理={} 已清理={} 有引用跳过={}",
                dryRun, report.archiveCandidates(), report.archived(),
                report.purgeCandidates(), report.purged(), report.skippedReferenced());
        if (report.archiveCandidates() > 0 || report.purgeCandidates() > 0) {
            notifyAdmins(report);
        }
        return report;
    }

    /** 有任何用户/交易数据引用就不物理删除（只归档，保留历史） */
    private boolean hasReferences(Long productId) {
        return count(orderItemMapper.selectCount(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getProductId, productId))) > 0
                || count(afterSaleItemMapper.selectCount(Wrappers.<AfterSaleItem>lambdaQuery()
                .eq(AfterSaleItem::getProductId, productId))) > 0
                || count(cartItemMapper.selectCount(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getProductId, productId))) > 0
                || count(reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getProductId, productId))) > 0
                || count(productQuestionMapper.selectCount(Wrappers.<ProductQuestion>lambdaQuery()
                .eq(ProductQuestion::getProductId, productId))) > 0
                || count(userFavoriteMapper.selectCount(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getProductId, productId))) > 0
                || count(userFootprintMapper.selectCount(Wrappers.<UserFootprint>lambdaQuery()
                .eq(UserFootprint::getProductId, productId))) > 0;
        // 价格历史是商品内部数据，随商品一起清理，不算“引用”
    }

    private void purge(Long productId) {
        List<ProductSpec> specs = productSpecMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                .eq(ProductSpec::getProductId, productId));
        if (!specs.isEmpty()) {
            List<Long> specIds = specs.stream().map(ProductSpec::getId).toList();
            productSpecValueMapper.delete(Wrappers.<ProductSpecValue>lambdaQuery()
                    .in(ProductSpecValue::getSpecId, specIds));
        }
        productSpecMapper.delete(Wrappers.<ProductSpec>lambdaQuery()
                .eq(ProductSpec::getProductId, productId));
        productSkuMapper.delete(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId));
        priceHistoryMapper.delete(Wrappers.<ProductPriceHistory>lambdaQuery()
                .eq(ProductPriceHistory::getProductId, productId));
        productMapper.deleteById(productId);
        cacheHelper.evict(RedisCacheHelper.key(CACHE_KEY_PREFIX + productId));
        log.info("已物理清理无引用归档商品: productId={}", productId);
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    private void notifyAdmins(ProductCleanupReport report) {
        try {
            String title = report.dryRun() ? "商品清理报告（dry-run）" : "商品清理完成";
            String content = String.format(
                    "%s：待归档 %d 个、已归档 %d 个；可清理 %d 个、已清理 %d 个；有引用跳过 %d 个。",
                    report.dryRun() ? "本次为预演，未修改数据" : "已执行",
                    report.archiveCandidates(), report.archived(),
                    report.purgeCandidates(), report.purged(), report.skippedReferenced());
            notificationService.notifyAdmins("PRODUCT_CLEANUP", title, content,
                    "/admin/products",
                    "product-cleanup:" + LocalDate.now() + (report.dryRun() ? ":dry" : ":run"));
        } catch (RuntimeException e) {
            log.warn("商品清理通知写入失败: {}", e.getMessage());
        }
    }
}
