package com.tsuk1.itsmygo.common.product;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.AfterSaleItem;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.ProductQuestion;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.UserFavorite;
import com.tsuk1.itsmygo.entity.UserFootprint;
import com.tsuk1.itsmygo.mapper.AfterSaleItemMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.ProductQuestionMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserFavoriteMapper;
import com.tsuk1.itsmygo.mapper.UserFootprintMapper;
import org.springframework.stereotype.Component;

/**
 * 商品是否被业务数据引用：订单/售后明细、购物车、评价、问大家、收藏、足迹。
 * 管理端硬删除和自动清理任务共用，避免两处规则漂移。
 * 价格历史属于商品自身数据，不算引用（随商品一起清理）。
 */
@Component
public class ProductReferenceChecker {

    private final OrderItemMapper orderItemMapper;
    private final AfterSaleItemMapper afterSaleItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ReviewMapper reviewMapper;
    private final ProductQuestionMapper productQuestionMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserFootprintMapper userFootprintMapper;

    public ProductReferenceChecker(OrderItemMapper orderItemMapper,
                                   AfterSaleItemMapper afterSaleItemMapper,
                                   CartItemMapper cartItemMapper,
                                   ReviewMapper reviewMapper,
                                   ProductQuestionMapper productQuestionMapper,
                                   UserFavoriteMapper userFavoriteMapper,
                                   UserFootprintMapper userFootprintMapper) {
        this.orderItemMapper = orderItemMapper;
        this.afterSaleItemMapper = afterSaleItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.reviewMapper = reviewMapper;
        this.productQuestionMapper = productQuestionMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userFootprintMapper = userFootprintMapper;
    }

    public boolean hasReferences(Long productId) {
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
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
