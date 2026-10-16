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
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final ProductSkuMapper skuMapper;

    public CartServiceImpl(CartItemMapper cartItemMapper, ProductMapper productMapper,
                           ProductSkuMapper skuMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
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
        ProductSku sku = requireSkuOf(request.skuId(), product.getId());
        if (request.quantity() > sku.getStock()) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不足");
        }

        cartItemMapper.addQuantity(userId, product.getId(), sku.getId(), request.quantity());
        return list(userId);
    }

    @Override
    public CartView update(Long userId, Long itemId, CartUpdateRequest request) {
        CartItem item = requireOwned(userId, itemId);

        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品已下架，无法修改数量");
        }
        ProductSku sku = requireSkuOf(item.getSkuId(), item.getProductId());
        if (request.quantity() > sku.getStock()) {
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
     * 组装视图：购物车行 + 商品与 SKU 的当前信息。
     * 商品和 SKU 各按 id **批量**查一次再内存拼装，避免逐行查库（N+1）。
     */
    private CartView buildView(List<CartItem> items) {
        if (items.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO);
        }

        Map<Long, Product> products = productMapper.selectByIds(
                        items.stream().map(CartItem::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<Long, ProductSku> skus = skuMapper.selectByIds(
                        items.stream().map(CartItem::getSkuId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        BigDecimal selectedTotal = BigDecimal.ZERO;
        List<CartItemView> views = new ArrayList<>(items.size());

        for (CartItem item : items) {
            Product product = products.get(item.getProductId());
            ProductSku sku = skus.get(item.getSkuId());
            boolean selected = item.getSelected() == 1;

            // 商品或规格没了：**不隐藏这一行**，让用户自己看到并删掉，比静默消失好
            if (product == null || sku == null) {
                views.add(new CartItemView(item.getId(), item.getProductId(), item.getSkuId(),
                        product == null ? "商品已删除" : product.getName(),
                        product == null ? "" : product.getMainImage(),
                        sku == null ? "" : sku.getSpecText(),
                        BigDecimal.ZERO, 0, item.getQuantity(), BigDecimal.ZERO,
                        selected, false, false));
                continue;
            }

            // 商品和它这一款都得在售
            boolean onSale = Integer.valueOf(ON_SALE).equals(product.getStatus())
                    && Integer.valueOf(ON_SALE).equals(sku.getStatus());
            boolean available = onSale && sku.getStock() >= item.getQuantity();
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            if (selected && available) {
                selectedTotal = selectedTotal.add(subtotal);
            }

            String image = StringUtils.hasText(sku.getImage()) ? sku.getImage() : product.getMainImage();

            views.add(new CartItemView(item.getId(), item.getProductId(), item.getSkuId(),
                    product.getName(), image, sku.getSpecText(),
                    sku.getPrice(), sku.getStock(), item.getQuantity(), subtotal,
                    selected, onSale, available));
        }

        return new CartView(views, selectedTotal);
    }

    /** SKU 必须存在、属于这个商品、且在售 —— 三者缺一都不该进购物车 */
    private ProductSku requireSkuOf(Long skuId, Long productId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品规格不存在");
        }
        if (!Integer.valueOf(ON_SALE).equals(sku.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该规格已停售");
        }
        return sku;
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
