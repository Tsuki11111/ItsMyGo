package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加购：一条原子 SQL 完成「不存在就插、已存在就累加」，而不是「先查再插/改」——
     * 后者在两个请求同时加购时会双双查到「不存在」、双双插入，其中一个必然撞唯一索引。
     *
     * 靠的是 {@code uk_user_sku (user_id, sku_id)} 这个唯一键：
     * **同一个人可以把同一商品的不同规格各放一行**，但同一个规格只有一行。
     */
    @Insert("""
            INSERT INTO cart_items (user_id, product_id, sku_id, quantity, selected)
            VALUES (#{userId}, #{productId}, #{skuId}, #{quantity}, 1)
            ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}
            """)
    int addQuantity(@Param("userId") Long userId, @Param("productId") Long productId,
                    @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
