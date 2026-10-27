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
}
