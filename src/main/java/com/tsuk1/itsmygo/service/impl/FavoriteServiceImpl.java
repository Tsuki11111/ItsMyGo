package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.FavoriteView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.UserFavorite;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.UserFavoriteMapper;
import com.tsuk1.itsmygo.service.FavoriteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteServiceImpl(UserFavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Override
    public PageResponse<FavoriteView> page(Long userId, int page, int size) {
        Page<UserFavorite> result = favoriteMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getId));

        List<UserFavorite> favorites = result.getRecords();
        // 两条 SQL：先分页查收藏，再按 productId 批量查商品（不 join，也不 N+1）
        Map<Long, Product> products = favorites.isEmpty() ? Map.of()
                : productMapper.selectByIds(favorites.stream().map(UserFavorite::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<FavoriteView> views = favorites.stream()
                .map(favorite -> toView(favorite, products.get(favorite.getProductId())))
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        return favoriteMapper.exists(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId));
    }

    @Override
    public void add(Long userId, Long productId) {
        requireProduct(productId);
        // 唯一键 + INSERT IGNORE：重复收藏保持幂等
        favoriteMapper.insertIgnore(userId, productId);
    }

    @Override
    public void remove(Long userId, Long productId) {
        favoriteMapper.delete(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId));
    }

    private FavoriteView toView(UserFavorite favorite, Product product) {
        if (product == null) {
            // 商品被硬删除（管理端不提供，但历史数据可能有）
            return new FavoriteView(favorite.getId(), favorite.getProductId(),
                    "商品已不存在", "", "", BigDecimal.ZERO, 0, favorite.getCreatedAt());
        }
        return new FavoriteView(favorite.getId(), product.getId(), product.getName(),
                product.getSubtitle(), product.getMainImage(), product.getPrice(),
                product.getStatus(), favorite.getCreatedAt());
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
