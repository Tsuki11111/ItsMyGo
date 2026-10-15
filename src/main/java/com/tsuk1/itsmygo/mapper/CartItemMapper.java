package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加购（重复加购则累加数量）。
     * 用一条原子 SQL 而不是「先查再插/改」——后者在两个请求同时加购同一商品时，
     * 会双双查到「不存在」然后双双插入，其中一个撞唯一索引报错。
     */
    @Insert("""
            INSERT INTO cart_items (user_id, product_id, quantity, selected)
            VALUES (#{userId}, #{productId}, #{quantity}, 1)
            ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}
            """)
    int addQuantity(@Param("userId") Long userId,
                    @Param("productId") Long productId,
                    @Param("quantity") Integer quantity);
}
