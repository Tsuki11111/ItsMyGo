package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminReviewView;
import com.tsuk1.itsmygo.dto.MyReviewView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ReviewRatingCount;
import com.tsuk1.itsmygo.dto.ReviewRequest;
import com.tsuk1.itsmygo.dto.ReviewSummary;
import com.tsuk1.itsmygo.dto.ReviewView;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.ReviewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VISIBLE = 1;
    private static final int HIDDEN = 0;

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;

    public ReviewServiceImpl(ReviewMapper reviewMapper, OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper, ProductMapper productMapper,
                             UserMapper userMapper, MerchantMapper merchantMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    @Transactional
    public void create(Long userId, ReviewRequest request) {
        OrderItem item = orderItemMapper.selectById(request.orderItemId());
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单项不存在");
        }
        Order order = orderMapper.selectById(item.getOrderId());
        // 别人的订单一律当不存在
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单项不存在");
        }
        if (!OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已完成订单可以评价");
        }
        if (reviewMapper.exists(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderItemId, item.getId()))) {
            throw new BizException(ResultCode.BAD_REQUEST, "该商品已评价");
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setProductId(item.getProductId());
        review.setMerchantId(order.getMerchantId());
        review.setSkuId(item.getSkuId());
        review.setRating(request.rating());
        review.setContent(request.content() == null ? "" : request.content().trim());
        review.setStatus(VISIBLE);

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发提交：唯一索引是最终防线，转成人话而不是 500
            throw new BizException(ResultCode.BAD_REQUEST, "该商品已评价");
        }
    }

    @Override
    public PageResponse<MyReviewView> mine(Long userId, int page, int size) {
        Page<Review> result = reviewMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId));

        List<Review> reviews = result.getRecords();
        // 这一页涉及的商品 / 店铺各查一次，避免每行查库（N+1）
        List<Long> productIds = new ArrayList<>(reviews.size());
        List<Long> merchantIds = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            productIds.add(review.getProductId());
            merchantIds.add(review.getMerchantId());
        }
        Map<Long, Product> products = loadProducts(productIds);
        Map<Long, Merchant> merchants = loadMerchants(merchantIds);

        List<MyReviewView> views = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            Product product = products.get(review.getProductId());
            Merchant merchant = merchants.get(review.getMerchantId());
            views.add(new MyReviewView(review.getId(), review.getOrderItemId(), review.getProductId(),
                    product == null ? "商品已不存在" : product.getName(),
                    product == null ? "" : product.getMainImage(),
                    review.getMerchantId(), merchant == null ? "" : merchant.getName(),
                    review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt()));
        }

        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResponse<ReviewView> productReviews(Long productId, int page, int size,
                                                   Integer rating) {
        requireProduct(productId);
        Page<Review> result = reviewMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getProductId, productId)
                        .eq(Review::getStatus, VISIBLE)
                        .eq(rating != null, Review::getRating, rating)
                        .orderByDesc(Review::getId));

        List<Review> reviews = result.getRecords();
        List<Long> userIds = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            userIds.add(review.getUserId());
        }
        Map<Long, User> users = loadUsers(userIds);

        List<ReviewView> views = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            User user = users.get(review.getUserId());
            String nickname = user == null || !StringUtils.hasText(user.getNickname())
                    ? "匿名用户" : user.getNickname();
            String avatar = user == null || user.getAvatar() == null ? "" : user.getAvatar();
            views.add(new ReviewView(review.getId(), review.getRating(), review.getContent(),
                    nickname, avatar, review.getCreatedAt()));
        }

        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ReviewSummary summary(Long productId) {
        requireProduct(productId);
        long count = reviewMapper.countVisibleByProduct(productId);
        double average = count == 0 ? 0
                : Math.round(reviewMapper.averageVisibleRating(productId) * 10) / 10.0;
        Map<Integer, Long> distribution = new java.util.LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        List<ReviewRatingCount> ratingRows = reviewMapper.countVisibleByRating(productId);
        for (ReviewRatingCount row : ratingRows) {
            distribution.put(row.getRating(), row.getCount());
        }
        return new ReviewSummary(average, count, distribution);
    }

    @Override
    public PageResponse<AdminReviewView> pageForAdmin(int page, int size, Boolean visible, String keyword) {
        return pageForAdmin(page, size, visible, keyword, null);
    }

    @Override
    public PageResponse<AdminReviewView> pageForAdmin(int page, int size, Boolean visible,
                                                      String keyword, Long merchantId) {
        Page<Review> result = reviewMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Review>lambdaQuery()
                        .eq(visible != null, Review::getStatus, Boolean.TRUE.equals(visible) ? VISIBLE : HIDDEN)
                        .eq(merchantId != null, Review::getMerchantId, merchantId)
                        .like(StringUtils.hasText(keyword), Review::getContent, keyword)
                        .orderByDesc(Review::getId));
        return toAdminPage(result);
    }

    @Override
    public PageResponse<AdminReviewView> pageForMerchant(Long merchantId, int page, int size,
                                                         Boolean visible, String keyword) {
        Page<Review> result = reviewMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getMerchantId, merchantId)
                        .eq(visible != null, Review::getStatus, Boolean.TRUE.equals(visible) ? VISIBLE : HIDDEN)
                        .like(StringUtils.hasText(keyword), Review::getContent, keyword)
                        .orderByDesc(Review::getId));
        return toAdminPage(result);
    }

    private PageResponse<AdminReviewView> toAdminPage(Page<Review> result) {
        List<Review> reviews = result.getRecords();
        List<Long> productIds = new ArrayList<>(reviews.size());
        List<Long> userIds = new ArrayList<>(reviews.size());
        List<Long> merchantIds = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            productIds.add(review.getProductId());
            userIds.add(review.getUserId());
            merchantIds.add(review.getMerchantId());
        }
        Map<Long, Product> products = loadProducts(productIds);
        Map<Long, User> users = loadUsers(userIds);
        Map<Long, Merchant> merchants = loadMerchants(merchantIds);

        List<AdminReviewView> views = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            Product product = products.get(review.getProductId());
            User user = users.get(review.getUserId());
            Merchant merchant = merchants.get(review.getMerchantId());
            views.add(new AdminReviewView(review.getId(), review.getUserId(),
                    user == null ? "未知用户" : user.getNickname(),
                    review.getMerchantId(), merchant == null ? "" : merchant.getName(),
                    review.getProductId(),
                    product == null ? "商品已不存在" : product.getName(),
                    review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt()));
        }

        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void setVisibleForMerchant(Long merchantId, Long reviewId, boolean visible) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || !merchantId.equals(review.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND, "评价不存在");
        }
        Review update = new Review();
        update.setId(reviewId);
        update.setStatus(visible ? VISIBLE : HIDDEN);
        reviewMapper.updateById(update);
    }

    @Override
    public void setVisible(Long reviewId, boolean visible) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评价不存在");
        }
        Review update = new Review();
        update.setId(reviewId);
        update.setStatus(visible ? VISIBLE : HIDDEN);
        reviewMapper.updateById(update);
    }

    private Map<Long, Product> loadProducts(List<Long> productIds) {
        Set<Long> ids = new HashSet<>();
        for (Long id : productIds) {
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, Product> result = new HashMap<>();
        for (Product product : productMapper.selectByIds(ids)) {
            result.put(product.getId(), product);
        }
        return result;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        Set<Long> ids = new HashSet<>();
        for (Long id : userIds) {
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, User> result = new HashMap<>();
        for (User user : userMapper.selectByIds(ids)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private Map<Long, Merchant> loadMerchants(List<Long> merchantIds) {
        Set<Long> ids = new HashSet<>();
        for (Long id : merchantIds) {
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, Merchant> result = new HashMap<>();
        for (Merchant merchant : merchantMapper.selectByIds(ids)) {
            result.put(merchant.getId(), merchant);
        }
        return result;
    }

    private void requireProduct(Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
