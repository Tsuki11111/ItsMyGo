package com.tsuk1.itsmygo.mapper;

import com.tsuk1.itsmygo.dto.AdminDailySales;
import com.tsuk1.itsmygo.dto.AdminLowStockSku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 管理后台概览的聚合查询。只读，不参与业务状态流转。 */
public interface AdminDashboardMapper {

    /** 已支付口径：PAID / SHIPPED / COMPLETED / REFUNDING，排除已退款与已取消 */
    @Select("""
            SELECT COALESCE(SUM(total_amount), 0) FROM orders
            WHERE paid_at >= #{start} AND paid_at < #{end}
              AND status IN ('PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING')
            """)
    BigDecimal sumPaidAmount(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Select("""
            SELECT DATE(paid_at) AS day,
                   COALESCE(SUM(total_amount), 0) AS amount,
                   COUNT(*) AS order_count
            FROM orders
            WHERE paid_at >= #{start} AND paid_at < #{end}
              AND status IN ('PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING')
            GROUP BY DATE(paid_at)
            ORDER BY day
            """)
    List<AdminDailySales> dailySales(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(*)
            FROM product_skus ps
            JOIN products p ON p.id = ps.product_id
            WHERE p.status = 1 AND ps.status = 1 AND ps.stock <= #{threshold}
            """)
    long countLowStock(@Param("threshold") int threshold);

    @Select("""
            SELECT p.id AS productId, p.name AS productName,
                   p.main_image AS image, ps.id AS skuId,
                   ps.spec_text AS specText, ps.stock
            FROM product_skus ps
            JOIN products p ON p.id = ps.product_id
            WHERE p.status = 1 AND ps.status = 1 AND ps.stock <= #{threshold}
            ORDER BY ps.stock ASC, ps.id DESC
            LIMIT #{limit}
            """)
    List<AdminLowStockSku> lowStock(@Param("threshold") int threshold,
                                    @Param("limit") int limit);
}
