package com.tsuk1.itsmygo.mapper;

import com.tsuk1.itsmygo.dto.report.ConsumptionMonthlyRow;
import com.tsuk1.itsmygo.dto.report.ConsumptionRankRow;
import com.tsuk1.itsmygo.dto.report.ConsumptionSummaryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人消费报告：全部是只读聚合，**不查模型、不加表**。
 *
 * <p>口径统一：`orders.paid_at` 落在期间内、状态属于
 * `PAID/SHIPPED/COMPLETED/REFUNDING/REFUNDED` 的订单算「已支付订单」。
 * 所有查询都强制带 `user_id`，报告天然按用户隔离。</p>
 */
public interface ReportMapper {

    /** 期间汇总：订单数、实付、已退、优惠。没有订单时 SUM 为 NULL，统一 COALESCE 成 0。 */
    @Select("""
            SELECT COUNT(*) AS orderCount,
                   COALESCE(SUM(total_amount), 0) AS paidAmount,
                   COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                   COALESCE(SUM(discount_amount), 0) AS discountAmount
            FROM orders
            WHERE user_id = #{userId}
              AND paid_at >= #{start} AND paid_at < #{end}
              AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            """)
    ConsumptionSummaryRow summarize(@Param("userId") Long userId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 品类 Top N：order_items → products → categories；商品被删时归入「未分类」。 */
    @Select("""
            SELECT COALESCE(c.name, '未分类') AS name,
                   COALESCE(SUM(oi.subtotal), 0) AS amount,
                   COALESCE(SUM(oi.quantity), 0) AS count
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE o.user_id = #{userId}
              AND o.paid_at >= #{start} AND o.paid_at < #{end}
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            GROUP BY c.id, c.name
            ORDER BY amount DESC, name
            LIMIT #{limit}
            """)
    List<ConsumptionRankRow> topCategories(@Param("userId") Long userId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end,
                                           @Param("limit") int limit);

    /** 店铺 Top N：直接按订单聚合（一单只属于一个商户），名字取 merchants.name。 */
    @Select("""
            SELECT COALESCE(m.name, '未知店铺') AS name,
                   COALESCE(SUM(o.total_amount), 0) AS amount
            FROM orders o
            LEFT JOIN merchants m ON m.id = o.merchant_id
            WHERE o.user_id = #{userId}
              AND o.paid_at >= #{start} AND o.paid_at < #{end}
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            GROUP BY o.merchant_id, m.name
            ORDER BY amount DESC, name
            LIMIT #{limit}
            """)
    List<ConsumptionRankRow> topMerchants(@Param("userId") Long userId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("limit") int limit);

    /** 商品 Top N：用下单快照名（商品改名/下架后历史报告仍准确），按金额排序。 */
    @Select("""
            SELECT oi.product_name AS name,
                   COALESCE(SUM(oi.subtotal), 0) AS amount,
                   COALESCE(SUM(oi.quantity), 0) AS count
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.user_id = #{userId}
              AND o.paid_at >= #{start} AND o.paid_at < #{end}
              AND o.status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            GROUP BY oi.product_id, oi.product_name
            ORDER BY amount DESC, name
            LIMIT #{limit}
            """)
    List<ConsumptionRankRow> topProducts(@Param("userId") Long userId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("limit") int limit);

    /** 按月消费：只返回有订单的月份，前端补零到 12 个月。 */
    @Select("""
            SELECT DATE_FORMAT(paid_at, '%Y-%m') AS month,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM orders
            WHERE user_id = #{userId}
              AND paid_at >= #{start} AND paid_at < #{end}
              AND status IN ('PAID','SHIPPED','COMPLETED','REFUNDING','REFUNDED')
            GROUP BY DATE_FORMAT(paid_at, '%Y-%m')
            ORDER BY month
            """)
    List<ConsumptionMonthlyRow> monthly(@Param("userId") Long userId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
