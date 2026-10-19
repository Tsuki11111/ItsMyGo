package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.FootprintView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.UserFootprint;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.UserFootprintMapper;
import com.tsuk1.itsmygo.service.FootprintService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FootprintServiceImpl implements FootprintService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 每个用户最多保留多少条足迹 */
    private static final int KEEP = 100;
    /** 超过这个数才清理，避免每次浏览都全表扫该用户的行 */
    private static final int TRIM_THRESHOLD = KEEP + 10;

    private final UserFootprintMapper footprintMapper;
    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;

    public FootprintServiceImpl(UserFootprintMapper footprintMapper, ProductMapper productMapper,
                                MerchantMapper merchantMapper) {
        this.footprintMapper = footprintMapper;
        this.productMapper = productMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public void record(Long userId, Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        footprintMapper.upsert(userId, productId);
        if (footprintMapper.countByUser(userId) > TRIM_THRESHOLD) {
            footprintMapper.trimToLatest(userId, KEEP);
        }
    }

    @Override
    public PageResponse<FootprintView> page(Long userId, int page, int size) {
        Page<UserFootprint> result = footprintMapper.selectPage(
                new Page<>(normalizePage(page), normalizeSize(size)),
                Wrappers.<UserFootprint>lambdaQuery()
                        .eq(UserFootprint::getUserId, userId)
                        .orderByDesc(UserFootprint::getViewedAt)
                        .orderByDesc(UserFootprint::getId));

        List<UserFootprint> footprints = result.getRecords();
        Map<Long, Product> products = footprints.isEmpty() ? Map.of()
                : productMapper.selectByIds(footprints.stream().map(UserFootprint::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Merchant> merchants = products.isEmpty() ? Map.of()
                : merchantMapper.selectByIds(products.values().stream().map(Product::getMerchantId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Merchant::getId, Function.identity()));

        List<FootprintView> views = footprints.stream()
                .map(footprint -> {
                    Product product = products.get(footprint.getProductId());
                    Merchant merchant = product == null ? null : merchants.get(product.getMerchantId());
                    return toView(footprint, product, merchant);
                })
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void clear(Long userId) {
        footprintMapper.delete(Wrappers.<UserFootprint>lambdaQuery()
                .eq(UserFootprint::getUserId, userId));
    }

    private FootprintView toView(UserFootprint footprint, Product product, Merchant merchant) {
        if (product == null) {
            return new FootprintView(footprint.getId(), footprint.getProductId(),
                    "商品已不存在", "", "", BigDecimal.ZERO, 0, footprint.getViewedAt(), null, "");
        }
        return new FootprintView(footprint.getId(), product.getId(), product.getName(),
                product.getSubtitle(), product.getMainImage(), product.getPrice(),
                product.getStatus(), footprint.getViewedAt(),
                product.getMerchantId(), merchant == null ? "" : merchant.getName());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
