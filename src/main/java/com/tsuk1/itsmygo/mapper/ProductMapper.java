package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 商品**汇总字段**的增量更新（下单扣减时）。
     *
     * ⚠️ 它**不是**防超卖的防线 —— 真正带 {@code WHERE stock >= ?} 的条件更新在
     * {@link ProductSkuMapper#deductStock}，那个才参与判定。
     * 这里只负责让列表页的库存/销量不至于显示旧数字，所以用 {@code GREATEST}
     * 容忍漂移：汇总偏一点，不该让下单失败。
     */
    @Update("""
            UPDATE products
            SET stock = GREATEST(stock - #{quantity}, 0),
                sales = sales + #{quantity}
            WHERE id = #{productId}
            """)
    int applyDeducted(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 同理，取消 / 退款时把汇总回滚回去 */
    @Update("""
            UPDATE products
            SET stock = stock + #{quantity},
                sales = CASE WHEN sales >= #{quantity} THEN sales - #{quantity} ELSE 0 END
            WHERE id = #{productId}
            """)
    int applyRestored(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 按 SKU 重算商品的汇总字段：价格取在售 SKU 的最低价，库存与销量求和，
     * 顺带刷新 {@code has_specs}（列表页靠它决定要不要显示「¥19.8 起」）。
     *
     * **只在管理端保存规格/SKU 时调**（低频操作）；下单那种高频路径走
     * {@link #applyStockDelta}，不在这里做聚合。
     */
    @Update("""
            UPDATE products
            SET price = COALESCE((SELECT MIN(price) FROM product_skus
                                  WHERE product_id = #{productId} AND status = 1), 0),
                stock = COALESCE((SELECT SUM(stock) FROM product_skus
                                  WHERE product_id = #{productId} AND status = 1), 0),
                sales = COALESCE((SELECT SUM(sales) FROM product_skus
                                  WHERE product_id = #{productId} AND status = 1), 0),
                has_specs = IF((SELECT COUNT(*) FROM product_specs
                                WHERE product_id = #{productId}) > 0, 1, 0)
            WHERE id = #{productId}
            """)
    int recalcFromSkus(@Param("productId") Long productId);
}
