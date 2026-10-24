package com.tsuk1.itsmygo.mapper;

import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminMerchantInventoryRow;
import com.tsuk1.itsmygo.dto.AdminMerchantSalesRow;
import com.tsuk1.itsmygo.dto.AdminTopProductRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 商户经营与商品运营的只读聚合查询。 */
public interface AdminOperationsMapper {

    @Select("""
            <script>
            SELECT merchant_id AS merchantId,
                   COALESCE(SUM(CASE WHEN paid_at &gt;= #{start} AND paid_at &lt; #{end}
                                      AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING')
                                     THEN total_amount ELSE 0 END), 0) AS gmv30,
                   COALESCE(SUM(CASE WHEN created_at &gt;= #{start} AND created_at &lt; #{end}
                                     THEN 1 ELSE 0 END), 0) AS orders30,
                   COALESCE(SUM(CASE WHEN created_at &gt;= #{start} AND created_at &lt; #{end}
                                      AND status IN ('REFUNDING','REFUNDED')
                                     THEN 1 ELSE 0 END), 0) AS refunding30
            FROM orders
            WHERE merchant_id IN
            <foreach collection="merchantIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY merchant_id
            </script>
            """)
    List<AdminMerchantSalesRow> merchantSales(@Param("merchantIds") List<Long> merchantIds,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Select("""
            <script>
            SELECT p.merchant_id AS merchantId,
                   COUNT(DISTINCT CASE WHEN p.status = 1 THEN p.id END) AS onSaleProducts,
                   COALESCE(SUM(CASE WHEN p.status = 1 AND ps.status = 1 AND ps.stock &lt;= #{threshold}
                                     THEN 1 ELSE 0 END), 0) AS lowStockSkus
            FROM products p
            LEFT JOIN product_skus ps ON ps.product_id = p.id
            WHERE p.merchant_id IN
            <foreach collection="merchantIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY p.merchant_id
            </script>
            """)
    List<AdminMerchantInventoryRow> merchantInventory(
            @Param("merchantIds") List<Long> merchantIds,
            @Param("threshold") int threshold);

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
    List<AdminDailySales> merchantDailySales(@Param("merchantId") Long merchantId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Select("""
            SELECT id AS productId, name, main_image AS image, price, stock, sales
            FROM products
            WHERE merchant_id = #{merchantId} AND status = 1
            ORDER BY sales DESC, id DESC
            LIMIT #{limit}
            """)
    List<AdminTopProductRow> topProducts(@Param("merchantId") Long merchantId,
                                         @Param("limit") int limit);
}
