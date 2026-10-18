package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.ProductSku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /**
     * 扣 SKU 库存 —— **防超卖的最终防线**。
     * 和当初 `products` 上那条是同一个套路，只是 id 从商品换成了 SKU：
     * {@code WHERE stock >= ?} 让「判断」和「扣减」在同一条 SQL 里完成（InnoDB 行锁保证原子），
     * 返回 0 就代表库存不足，不会有「查的时候够、扣的时候不够」的窗口。
     */
    @Update("""
            UPDATE product_skus
            SET stock = stock - #{quantity}, sales = sales + #{quantity}
            WHERE id = #{skuId} AND status = 1 AND stock >= #{quantity}
            """)
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 取消订单时回滚 SKU 库存（销量也退回去） */
    @Update("""
            UPDATE product_skus
            SET stock = stock + #{quantity},
                sales = CASE WHEN sales >= #{quantity} THEN sales - #{quantity} ELSE 0 END
            WHERE id = #{skuId}
            """)
    int restoreStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 管理端调整库存 —— **条件更新**，防止把并发的下单/取消冲掉。
     *
     * 只有库里还是 {@code expectedStock} 时才写成 {@code stock}；
     * 返回 0 表示期间库存已经变了，调用方应该让商户刷新后重试，
     * 而不是硬覆盖（那会丢更新）。
     */
    @Update("""
            UPDATE product_skus
            SET stock = #{stock}
            WHERE id = #{skuId} AND product_id = #{productId} AND stock = #{expectedStock}
            """)
    int compareAndSetStock(@Param("productId") Long productId,
                           @Param("skuId") Long skuId,
                           @Param("stock") Integer stock,
                           @Param("expectedStock") Integer expectedStock);
}
