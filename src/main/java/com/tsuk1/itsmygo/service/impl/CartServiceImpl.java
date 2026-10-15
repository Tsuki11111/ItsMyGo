package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CartItemView;
import com.tsuk1.itsmygo.dto.CartUpdateRequest;
import com.tsuk1.itsmygo.dto.CartView;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.CartService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private static final int ON_SALE = 1;
    private static final int MAX_QUANTITY = 999;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public CartServiceImpl(CartItemMapper cartItemMapper, ProductMapper productMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
    }

    @Override
    public CartView list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getId));
        return buildView(items);
    }

    @Override
    public CartView add(Long userId, CartAddRequest request) {
        Product product = productMapper.selectById(request.productId());
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        if (request.quantity() > product.getStock()) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }

        cartItemMapper.addQuantity(userId, request.productId(), request.quantity());
        return list(userId);
    }

    @Override
    public CartView update(Long userId, Long itemId, CartUpdateRequest request) {
        CartItem item = requireOwned(userId, itemId);

        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品已下架，无法修改数量");
        }
        if (request.quantity() > product.getStock()) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }

        item.setQuantity(Math.min(request.quantity(), MAX_QUANTITY));
        item.setSelected(Boolean.TRUE.equals(request.selected()) ? 1 : 0);
        cartItemMapper.updateById(item);
        return list(userId);
    }

    @Override
    public CartView remove(Long userId, Long itemId) {
        requireOwned(userId, itemId);
        cartItemMapper.deleteById(itemId);
        return list(userId);
    }

    /**
     * 组装视图：购物车行 + 商品当前信息。
     * 先按 productId 批量查商品再内存拼装，避免逐行查库（N+1）。
     */
    private CartView buildView(List<CartItem> items) {
        if (items.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO);
        }

        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> products = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        BigDecimal selectedTotal = BigDecimal.ZERO;
        List<CartItemView> views = new ArrayList<>(items.size());

        for (CartItem item : items) {
            Product product = products.get(item.getProductId());

            // 商品被删除了：不隐藏这一行，让用户自己看到并删掉，比静默消失好
            if (product == null) {
                views.add(new CartItemView(item.getId(), item.getProductId(),
                        "商品已删除", "", BigDecimal.ZERO, 0, item.getQuantity(),
                        BigDecimal.ZERO, item.getSelected() == 1, false, false));
                continue;
            }

            boolean onSale = Integer.valueOf(ON_SALE).equals(product.getStatus());
            boolean available = onSale && product.getStock() >= item.getQuantity();
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            boolean selected = item.getSelected() == 1;

            if (selected && available) {
                selectedTotal = selectedTotal.add(subtotal);
            }

            views.add(new CartItemView(item.getId(), item.getProductId(),
                    product.getName(), product.getMainImage(),
                    product.getPrice(), product.getStock(), item.getQuantity(),
                    subtotal, selected, onSale, available));
        }

        return new CartView(views, selectedTotal);
    }

    /** 越权校验的唯一出口：别人的购物车行一律当「不存在」 */
    private CartItem requireOwned(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "购物车项不存在");
        }
        return item;
    }
}
