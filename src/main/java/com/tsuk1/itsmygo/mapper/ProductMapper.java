package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣库存 —— **防超卖的最终防线**。
     * {@code WHERE stock >= ?} 让「判断」和「扣减」在同一条 SQL 里完成（InnoDB 行锁保证原子），
     * 返回 0 就代表库存不足，不会有「查的时候够、扣的时候不够」的窗口。
     *
     * 用 {@code stock = stock - ?} 而不是先查出来再写回：后者并发下会互相覆盖。
     */
    @Update("""
            UPDATE products
            SET stock = stock - #{quantity}, sales = sales + #{quantity}
            WHERE id = #{productId} AND status = 1 AND stock >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 取消订单时回滚库存（销量也退回去） */
    @Update("""
            UPDATE products
            SET stock = stock + #{quantity},
                sales = CASE WHEN sales >= #{quantity} THEN sales - #{quantity} ELSE 0 END
            WHERE id = #{productId}
            """)
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
