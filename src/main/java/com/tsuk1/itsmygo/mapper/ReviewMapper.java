package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.dto.ProductReviewStat;
import com.tsuk1.itsmygo.dto.ReviewRatingCount;
import com.tsuk1.itsmygo.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ReviewMapper extends BaseMapper<Review> {

    /** 商品公开评价的汇总：只统计未隐藏的 */
    @Select("""
            SELECT COUNT(*) FROM reviews
            WHERE product_id = #{productId} AND status = 1
            """)
    long countVisibleByProduct(@Param("productId") Long productId);

    /** 平均分；没有评价时返回 0 */
    @Select("""
            SELECT COALESCE(AVG(rating), 0) FROM reviews
            WHERE product_id = #{productId} AND status = 1
            """)
    double averageVisibleRating(@Param("productId") Long productId);

    /** 各星级可见评价数，商品详情页画评分分布 */
    @Select("""
            SELECT rating, COUNT(*) AS count FROM reviews
            WHERE product_id = #{productId} AND status = 1
            GROUP BY rating
            """)
    List<ReviewRatingCount> countVisibleByRating(@Param("productId") Long productId);

    /** 商品列表批量回填评分 / 评价数；空列表调用方要自己挡掉，避免非法 IN () */
    @Select("""
            <script>
            SELECT product_id AS productId, COUNT(*) AS reviewCount,
                   COALESCE(AVG(rating), 0) AS averageRating
            FROM reviews
            WHERE status = 1 AND product_id IN
            <foreach collection="productIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY product_id
            </script>
            """)
    List<ProductReviewStat> statsByProductIds(@Param("productIds") List<Long> productIds);

    /** 给订单页回填 reviewed：一次查一页订单的所有订单项 */
    @Select("""
            <script>
            SELECT order_item_id FROM reviews
            WHERE order_item_id IN
            <foreach collection="orderItemIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> findReviewedOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);
}
