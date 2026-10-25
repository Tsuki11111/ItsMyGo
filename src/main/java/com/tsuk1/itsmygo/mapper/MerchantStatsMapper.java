package com.tsuk1.itsmygo.mapper;

import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminLowStockSku;
import com.tsuk1.itsmygo.dto.AdminMerchantInventoryRow;
import com.tsuk1.itsmygo.dto.AdminTopProductRow;
import com.tsuk1.itsmygo.dto.MerchantStatsSummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 商家后台经营看板：所有查询都强制带 merchant_id，只读。 */
public interface MerchantStatsMapper {

    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN paid_at >= #{start30} AND paid_at < #{end}
                                  AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING')
                                 THEN total_amount ELSE 0 END), 0) AS gmv30,
                COALESCE(SUM(CASE WHEN paid_at >= #{start30} AND paid_at < #{end}
                                  AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING')
                                 THEN 1 ELSE 0 END), 0) AS paidOrders30,
                COALESCE(SUM(CASE WHEN created_at >= #{start30} AND created_at < #{end}
                                 THEN 1 ELSE 0 END), 0) AS orders30,
                COALESCE(SUM(CASE WHEN created_at >= #{start30} AND created_at < #{end}
                                  AND status IN ('REFUNDING','REFUNDED')
                                 THEN 1 ELSE 0 END), 0) AS refunding30,
                COALESCE(SUM(CASE WHEN paid_at >= #{todayStart} AND paid_at < #{end}
                                  AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING')
                                 THEN total_amount ELSE 0 END), 0) AS todayGmv,
                COALESCE(SUM(CASE WHEN created_at >= #{todayStart} AND created_at < #{end}
                                 THEN 1 ELSE 0 END), 0) AS todayOrders,
                COALESCE(SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END), 0) AS pendingShipment,
                COALESCE(SUM(CASE WHEN status = 'REFUNDING' THEN 1 ELSE 0 END), 0) AS pendingRefund
            FROM orders
            WHERE merchant_id = #{merchantId}
            """)
    MerchantStatsSummary summary(@Param("merchantId") Long merchantId,
                                 @Param("start30") LocalDateTime start30,
                                 @Param("todayStart") LocalDateTime todayStart,
                                 @Param("end") LocalDateTime end);

    @Select("""
            SELECT DATE(paid_at) AS day,
                   COALESCE(SUM(total_amount), 0) AS amount,
                   COUNT(*) AS orderCount
            FROM orders
            WHERE merchant_id = #{merchantId}
              AND paid_at >= #{start} AND paid_at < #{end}
              AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING')
            GROUP BY DATE(paid_at)
            ORDER BY day
            """)
    List<AdminDailySales> dailySales(@Param("merchantId") Long merchantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(DISTINCT CASE WHEN p.status = 1 THEN p.id END) AS onSaleProducts,
                   COALESCE(SUM(CASE WHEN p.status = 1 AND ps.status = 1
                                      AND ps.stock <= #{threshold}
                                     THEN 1 ELSE 0 END), 0) AS lowStockSkus
            FROM products p
            LEFT JOIN product_skus ps ON ps.product_id = p.id
            WHERE p.merchant_id = #{merchantId}
            """)
    AdminMerchantInventoryRow inventory(@Param("merchantId") Long merchantId,
                                        @Param("threshold") int threshold);

    @Select("""
            SELECT id AS productId, name, main_image AS image, price, stock, sales
            FROM products
            WHERE merchant_id = #{merchantId} AND status = 1
            ORDER BY sales DESC, id DESC
            LIMIT #{limit}
            """)
    List<AdminTopProductRow> topProducts(@Param("merchantId") Long merchantId,
                                         @Param("limit") int limit);

    @Select("""
            SELECT p.id AS productId, p.name AS productName,
                   p.main_image AS image, ps.id AS skuId,
                   ps.spec_text AS specText, ps.stock
            FROM product_skus ps
            JOIN products p ON p.id = ps.product_id
            WHERE p.merchant_id = #{merchantId}
              AND p.status = 1 AND ps.status = 1 AND ps.stock <= #{threshold}
            ORDER BY ps.stock ASC, ps.id DESC
            LIMIT #{limit}
            """)
    List<AdminLowStockSku> lowStock(@Param("merchantId") Long merchantId,
                                    @Param("threshold") int threshold,
                                    @Param("limit") int limit);
}
