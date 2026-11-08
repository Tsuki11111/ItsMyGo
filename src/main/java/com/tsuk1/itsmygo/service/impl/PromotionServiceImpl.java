package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.dto.ProductReviewStat;
import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.common.promotion.PromotionAgentNotifier;
import com.tsuk1.itsmygo.service.ProductPriceHistoryService;
import com.tsuk1.itsmygo.service.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 智能推广打分：降价 0.45 / 相似 0.25 / 销量 0.20 / 评分 0.10。
 *
 * <p>所有数字都来自真实数据：降价来自 {@code product_price_history}，
 * 相似来自用户最近 5 笔已支付订单的商品品类/品牌，销量来自 {@code products.sales}，
 * 评分来自可见评价均分。没有数据就是 0 分，不编数字，也不发没有信号的商品。</p>
 */
@Slf4j
@Service
public class PromotionServiceImpl implements PromotionService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int PRICE_LOOKBACK_DAYS = 30;
    private static final int PURCHASE_LOOKBACK_DAYS = 30;
    private static final int SIMILAR_ORDER_LIMIT = 5;

    private static final double WEIGHT_PRICE_DROP = 0.45;
    private static final double WEIGHT_SIMILARITY = 0.25;
    private static final double WEIGHT_SALES = 0.20;
    private static final double WEIGHT_RATING = 0.10;

    /** 一次推送最多带几件商品；①②③ 与前端卡片顺序一一对应 */
    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤"};
    private static final int MAX_PRODUCT_CAP = CIRCLED.length;

    private final AgentPreferenceMapper preferenceMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ReviewMapper reviewMapper;
    private final ProductPriceHistoryService priceHistoryService;
    private final PromotionAgentNotifier agentNotifier;
    private final ObjectProvider<PromotionService> selfProvider;
    private final int batchSize;
    private final int maxProducts;

    public PromotionServiceImpl(AgentPreferenceMapper preferenceMapper,
                                UserMapper userMapper,
                                ProductMapper productMapper,
                                OrderMapper orderMapper,
                                OrderItemMapper orderItemMapper,
                                ReviewMapper reviewMapper,
                                ProductPriceHistoryService priceHistoryService,
                                PromotionAgentNotifier agentNotifier,
                                ObjectProvider<PromotionService> selfProvider,
                                @Value("${itsmygo.promotion.batch-size:200}") int batchSize,
                                @Value("${itsmygo.promotion.max-products:3}") int maxProducts) {
        this.preferenceMapper = preferenceMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.reviewMapper = reviewMapper;
        this.priceHistoryService = priceHistoryService;
        this.agentNotifier = agentNotifier;
        this.selfProvider = selfProvider;
        this.batchSize = batchSize;
        this.maxProducts = Math.max(1, Math.min(maxProducts, MAX_PRODUCT_CAP));
    }

    @Override
    @Transactional
    public boolean notifyForUser(long userId) {
        AgentPreference preference = preferenceMapper.selectById(userId);
        if (preference == null || !Boolean.TRUE.equals(preference.getPromotionEnabled())) {
            return false;
        }
        LocalDate today = LocalDate.now(SHANGHAI);
        if (today.equals(preference.getLastPromotionDate())) {
            return false;
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            log.warn("智能推广跳过：用户 {} 不存在或已禁用", userId);
            return false;
        }

        List<Product> candidates = new ArrayList<>(productMapper.selectPromotionCandidates());
        if (candidates.isEmpty()) {
            return false;
        }
        Set<Long> recentlyBought = new HashSet<>(
                orderItemMapper.selectPurchasedProductIdsWithinDays(userId, PURCHASE_LOOKBACK_DAYS));
        candidates.removeIf(product -> recentlyBought.contains(product.getId()));
        if (candidates.isEmpty()) {
            return false;
        }

        Similarity similarity = loadSimilarity(userId);
        List<Long> candidateIds = candidates.stream().map(Product::getId).toList();
        Map<Long, BigDecimal> pricePeaks = priceHistoryService.maxPricesSince(
                candidateIds, LocalDateTime.now().minusDays(PRICE_LOOKBACK_DAYS));
        Map<Long, Double> ratings = loadRatings(candidateIds);
        int maxSales = candidates.stream()
                .mapToInt(this::salesOf)
                .max()
                .orElse(0);

        List<Scored> ranked = new ArrayList<>();
        for (Product product : candidates) {
            double priceDrop = priceDrop(product, pricePeaks.get(product.getId()));
            boolean categoryHit = product.getCategoryId() != null
                    && similarity.categoryIds().contains(product.getCategoryId());
            boolean brandHit = StringUtils.hasText(product.getBrand())
                    && similarity.brands().contains(product.getBrand());
            double similarScore = (categoryHit ? 0.5 : 0) + (brandHit ? 0.5 : 0);
            double salesScore = maxSales > 0 ? (double) salesOf(product) / maxSales : 0;
            double rating = ratings.getOrDefault(product.getId(), 0.0);
            double ratingScore = clamp(rating / 5.0);
            double score = priceDrop * WEIGHT_PRICE_DROP
                    + similarScore * WEIGHT_SIMILARITY
                    + clamp(salesScore) * WEIGHT_SALES
                    + ratingScore * WEIGHT_RATING;
            if (score <= 0) {
                continue;
            }
            ranked.add(new Scored(product, score, priceDrop, categoryHit, brandHit, rating));
        }
        if (ranked.isEmpty()) {
            log.debug("智能推广跳过：用户 {} 没有得分大于 0 的商品", userId);
            return false;
        }
        ranked.sort(Comparator.comparingDouble(Scored::score)
                .thenComparingInt(scored -> salesOf(scored.product()))
                .reversed());
        List<Scored> picked = new ArrayList<>(
                ranked.subList(0, Math.min(maxProducts, ranked.size())));
        String text = buildPromotionText(picked);
        // 推广写进「客服智能体」会话历史（不是顾客—店铺聊天），一次带多件商品
        List<Product> products = picked.stream().map(Scored::product).toList();
        if (!agentNotifier.deliver(userId, products, text)) {
            return false;
        }
        preferenceMapper.markPromotionSent(userId, today);
        log.info("智能推广已发送：用户 {}，商品 {} 件，最高分 {}", userId, products.size(),
                String.format(Locale.ROOT, "%.3f", picked.get(0).score()));
        return true;
    }

    @Override
    public int runDaily() {
        List<Long> userIds = preferenceMapper.selectPromotionUserIds(batchSize);
        int sent = 0;
        PromotionService self = selfProvider.getObject();
        for (Long userId : userIds) {
            try {
                if (self.notifyForUser(userId)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                // 单个用户失败不影响整批；事务由 notifyForUser 自己回滚
                log.warn("智能推广处理用户 {} 失败，跳过：{}", userId, e.getMessage());
            }
        }
        if (!userIds.isEmpty()) {
            log.info("智能推广批量完成：候选用户 {} 人，成功发送 {} 人", userIds.size(), sent);
        }
        return sent;
    }

    // ── 信号 ──

    /** 最近 30 天最高价 vs 现价；没有历史或现价为空 = 0 分 */
    private double priceDrop(Product product, BigDecimal peak) {
        BigDecimal latest = product.getPrice();
        if (peak == null || peak.signum() <= 0 || latest == null) {
            return 0;
        }
        double ratio = peak.subtract(latest).doubleValue() / peak.doubleValue();
        return clamp(ratio);
    }

    private Similarity loadSimilarity(long userId) {
        List<Long> orderIds = orderMapper.selectRecentPaidOrderIds(userId, SIMILAR_ORDER_LIMIT);
        if (orderIds.isEmpty()) {
            return Similarity.EMPTY;
        }
        List<Long> productIds = orderItemMapper.selectProductIdsByOrderIds(orderIds);
        if (productIds.isEmpty()) {
            return Similarity.EMPTY;
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<String> brands = new HashSet<>();
        for (Product product : productMapper.selectBatchIds(productIds)) {
            if (product.getCategoryId() != null) {
                categoryIds.add(product.getCategoryId());
            }
            if (StringUtils.hasText(product.getBrand())) {
                brands.add(product.getBrand());
            }
        }
        return new Similarity(categoryIds, brands);
    }

    private Map<Long, Double> loadRatings(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> ratings = new HashMap<>();
        for (ProductReviewStat stat : reviewMapper.statsByProductIds(productIds)) {
            if (stat.getProductId() != null && stat.getAverageRating() != null) {
                ratings.put(stat.getProductId(), stat.getAverageRating());
            }
        }
        return ratings;
    }

    /** 文案只拼真实信号，不编数字 */
    /** 多商品文案：按最强卖点选开头 + ①②③ 逐条真实卖点 + 一句统一 CTA */
    private String buildPromotionText(List<Scored> picked) {
        StringBuilder text = new StringBuilder(buildPromotionIntro(picked));
        for (int i = 0; i < picked.size(); i++) {
            text.append("\n").append(CIRCLED[i]).append(" ").append(buildSignals(picked.get(i)));
        }
        text.append("\n点商品卡看详情，想买哪件直接回我，我帮您下单～");
        return text.toString();
    }

    /** 开头按最强信号换说法，避免每条推荐都一个腔调 */
    private String buildPromotionIntro(List<Scored> picked) {
        Scored top = picked.get(0);
        String count = picked.size() == 1 ? "一件" : picked.size() + " 件";
        if (top.priceDrop() >= 0.05) {
            return "最近有几件价格挺合适，帮您挑出来了：";
        }
        if (top.categoryHit() || top.brandHit()) {
            return "按您之前买过的类型挑了几件，看看合不合心意：";
        }
        if (top.rating() >= 4.5) {
            return "最近口碑不错的几件，帮您整理好了：";
        }
        return "给您找了 " + count + " 最近值得看的，帮您整理好了：";
    }

    /** 单件商品的真实卖点，不编数字（不带 CTA，CTA 由整体文案统一收） */
    private String buildSignals(Scored scored) {
        List<String> parts = new ArrayList<>();
        if (scored.priceDrop() > 0) {
            double percent = Math.round(scored.priceDrop() * 1000) / 10.0;
            if (percent >= 0.1) {
                parts.add("近 30 天降了约 " + formatNumber(percent) + "%");
            } else {
                parts.add("近 30 天价格有下降");
            }
        }
        if (scored.categoryHit() && scored.brandHit()) {
            parts.add("和您之前买过的同品牌同类");
        } else if (scored.categoryHit()) {
            parts.add("和您之前买过的同类");
        } else if (scored.brandHit()) {
            parts.add("和您之前买过的同品牌");
        }
        if (scored.rating() > 0) {
            parts.add(String.format(Locale.ROOT, "评分 %.1f 分", scored.rating()));
        }
        int sales = salesOf(scored.product());
        if (sales > 0) {
            parts.add("已售 " + sales + " 件");
        }
        return String.join("，", parts);
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private int salesOf(Product product) {
        return product.getSales() == null ? 0 : product.getSales();
    }

    private double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 1);
    }

    private record Similarity(Set<Long> categoryIds, Set<String> brands) {
        static final Similarity EMPTY = new Similarity(Set.of(), Set.of());
    }

    private record Scored(Product product, double score, double priceDrop,
                          boolean categoryHit, boolean brandHit, double rating) {
    }
}
