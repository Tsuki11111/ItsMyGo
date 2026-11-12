package com.tsuk1.itsmygo.mapper;

import com.tsuk1.itsmygo.dto.profile.ProfileBrandRow;
import com.tsuk1.itsmygo.dto.profile.ProfileCategoryRow;
import com.tsuk1.itsmygo.dto.profile.ProfilePriceRow;
import com.tsuk1.itsmygo.dto.profile.ProfileSummaryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户画像的只读聚合查询。
 *
 * <p>口径与消费报告一致：只统计 {@code paid_at} 非空且状态属于已支付集合的订单；
 * 金额用「实付 - 已退款」作为净支出。所有查询都强制按 userId 隔离。</p>
 */
public interface UserProfileMapper {

    @Select("""
            SELECT COUNT(*) AS orderCount,
                   COALESCE(SUM(total_amount - COALESCE(refunded_amount, 0)), 0) AS paidAmount,
                   MAX(paid_at) AS lastOrderAt
            FROM orders
            WHERE user_id = #{userId}
              AND paid_at IS NOT NULL
              AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            """)
    ProfileSummaryRow summarize(@Param("userId") Long userId);

    @Select("""
            SELECT p.category_id AS categoryId,
                   COALESCE(c.name, '未分类') AS name,
                   COUNT(*) AS count,
                   COALESCE(SUM(oi.subtotal), 0) AS amount
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE o.user_id = #{userId}
              AND o.paid_at IS NOT NULL
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            GROUP BY p.category_id, c.name
            ORDER BY amount DESC, count DESC, name
            LIMIT #{limit}
            """)
    List<ProfileCategoryRow> topCategories(@Param("userId") Long userId,
                                           @Param("limit") int limit);

    @Select("""
            SELECT p.brand AS name, COUNT(*) AS count
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            WHERE o.user_id = #{userId}
              AND o.paid_at IS NOT NULL
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
              AND p.brand IS NOT NULL AND p.brand <> ''
            GROUP BY p.brand
            ORDER BY count DESC, name
            LIMIT #{limit}
            """)
    List<ProfileBrandRow> topBrands(@Param("userId") Long userId,
                                    @Param("limit") int limit);

    @Select("""
            SELECT COALESCE(MIN(oi.unit_price), 0) AS minPrice,
                   COALESCE(AVG(oi.unit_price), 0) AS avgPrice,
                   COALESCE(MAX(oi.unit_price), 0) AS maxPrice
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.user_id = #{userId}
              AND o.paid_at IS NOT NULL
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            """)
    ProfilePriceRow priceBand(@Param("userId") Long userId);

    @Select("""
            SELECT p.category_id AS categoryId,
                   COALESCE(c.name, '未分类') AS name,
                   COUNT(*) AS count,
                   0 AS amount
            FROM user_footprints f
            LEFT JOIN products p ON p.id = f.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE f.user_id = #{userId}
            GROUP BY p.category_id, c.name
            ORDER BY MAX(f.viewed_at) DESC, count DESC, name
            LIMIT #{limit}
            """)
    List<ProfileCategoryRow> recentViewCategories(@Param("userId") Long userId,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT p.category_id AS categoryId,
                   COALESCE(c.name, '未分类') AS name,
                   COUNT(*) AS count,
                   0 AS amount
            FROM user_favorites f
            LEFT JOIN products p ON p.id = f.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE f.user_id = #{userId}
            GROUP BY p.category_id, c.name
            ORDER BY count DESC, name
            LIMIT #{limit}
            """)
    List<ProfileCategoryRow> favoriteCategories(@Param("userId") Long userId,
                                                @Param("limit") int limit);

    @Select("""
            SELECT AVG(rating) FROM reviews
            WHERE user_id = #{userId} AND status = 1
            """)
    Double averageRating(@Param("userId") Long userId);
}
