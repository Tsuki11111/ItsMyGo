package com.tsuk1.itsmygo.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.tsuk1.itsmygo.dto.profile.ProfileBrandRow;
import com.tsuk1.itsmygo.dto.profile.ProfileCategoryRow;
import com.tsuk1.itsmygo.dto.profile.ProfilePriceRow;
import com.tsuk1.itsmygo.dto.profile.ProfileSummaryRow;
import com.tsuk1.itsmygo.dto.profile.UserProfileView;
import com.tsuk1.itsmygo.mapper.UserProfileMapper;
import com.tsuk1.itsmygo.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户画像：按需聚合 + Redis 缓存 30 分钟。
 *
 * <p>不做 LLM 总结、不落画像表：画像只由 SQL 聚合事实组成，事实变化后最多 30 分钟可见；
 * 数据量增大或聚合变慢时再升级为快照表。缓存/Redis 异常一律回退到实时计算。</p>
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final String CACHE_PREFIX = "its-my-go:agent:profile:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int CATEGORY_LIMIT = 5;
    private static final int BRAND_LIMIT = 3;
    private static final int RECENT_LIMIT = 3;

    private final UserProfileMapper profileMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public UserProfileServiceImpl(UserProfileMapper profileMapper,
                                  StringRedisTemplate redis,
                                  ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserProfileView profile(long userId) {
        String key = CACHE_PREFIX + userId;
        String cached = readCache(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, UserProfileView.class);
            } catch (Exception e) {
                log.warn("用户画像缓存解析失败，回退实时计算：userId={}，{}", userId, e.getMessage());
            }
        }

        UserProfileView view = compute(userId);
        writeCache(key, view);
        return view;
    }

    private UserProfileView compute(long userId) {
        ProfileSummaryRow summary = profileMapper.summarize(userId);
        long orderCount = summary == null || summary.getOrderCount() == null
                ? 0 : summary.getOrderCount();
        BigDecimal paid = money(summary == null ? null : summary.getPaidAmount());
        BigDecimal avg = orderCount == 0
                ? BigDecimal.ZERO
                : paid.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        LocalDate lastOrderAt = summary == null || summary.getLastOrderAt() == null
                ? null : summary.getLastOrderAt().toLocalDate();

        List<UserProfileView.CategoryPref> topCategories = profileMapper
                .topCategories(userId, CATEGORY_LIMIT).stream()
                .map(UserProfileServiceImpl::category)
                .toList();
        List<UserProfileView.BrandPref> topBrands = profileMapper
                .topBrands(userId, BRAND_LIMIT).stream()
                .map(row -> new UserProfileView.BrandPref(
                        row.getName(), count(row.getCount())))
                .toList();
        ProfilePriceRow price = profileMapper.priceBand(userId);
        UserProfileView.PriceBand priceBand = new UserProfileView.PriceBand(
                money(price == null ? null : price.getMinPrice()),
                money(price == null ? null : price.getAvgPrice()),
                money(price == null ? null : price.getMaxPrice()));
        List<UserProfileView.CategoryPref> recentViews = profileMapper
                .recentViewCategories(userId, RECENT_LIMIT).stream()
                .map(UserProfileServiceImpl::category)
                .toList();
        List<UserProfileView.CategoryPref> favorites = profileMapper
                .favoriteCategories(userId, RECENT_LIMIT).stream()
                .map(UserProfileServiceImpl::category)
                .toList();
        Double avgRating = profileMapper.averageRating(userId);

        boolean coldStart = orderCount == 0 && recentViews.isEmpty() && favorites.isEmpty();
        return new UserProfileView(coldStart, orderCount, paid, avg, lastOrderAt,
                topCategories, topBrands, priceBand, recentViews, favorites, avgRating);
    }

    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("用户画像缓存读取失败，回退实时计算：{}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, UserProfileView view) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(view), CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("用户画像缓存写入失败，不影响本次返回：{}", e.getMessage());
        }
    }

    private static UserProfileView.CategoryPref category(ProfileCategoryRow row) {
        return new UserProfileView.CategoryPref(
                row.getCategoryId(), row.getName(), count(row.getCount()), money(row.getAmount()));
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
