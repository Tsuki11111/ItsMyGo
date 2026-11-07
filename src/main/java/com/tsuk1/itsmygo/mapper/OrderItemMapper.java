package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /** 判断用户是否真的买过某商品；取消/退款/待支付不算，用于「问大家」回答权限 */
    @Select("""
            SELECT COUNT(*)
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.product_id = #{productId}
              AND o.user_id = #{userId}
              AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
            """)
    long countPurchasedProduct(@Param("userId") Long userId,
                               @Param("productId") Long productId);

    /** 智能推广：用户最近 30 天买过的 productId，用于排除重复推荐。 */
    @Select("""
            SELECT DISTINCT oi.product_id
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.user_id = #{userId}
              AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
              AND COALESCE(o.paid_at, o.created_at) >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            """)
    java.util.List<Long> selectPurchasedProductIdsWithinDays(@Param("userId") Long userId,
                                                            @Param("days") int days);

    /** 智能推广：给定订单里的商品（用于算用户最近买过的品类/品牌）。 */
    @Select("""
            <script>
            SELECT DISTINCT product_id FROM order_items
            WHERE order_id IN
            <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    java.util.List<Long> selectProductIdsByOrderIds(@Param("orderIds") java.util.List<Long> orderIds);
}
