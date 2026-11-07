package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.dto.ProductPricePeak;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductPriceHistoryMapper extends BaseMapper<ProductPriceHistory> {

    /** 批量取商品在窗口内的最高价；没有历史的商品不会出现在结果里。 */
    @Select("""
            <script>
            SELECT product_id AS productId, MAX(price) AS maxPrice
            FROM product_price_history
            WHERE changed_at &gt;= #{since} AND product_id IN
            <foreach collection="productIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY product_id
            </script>
            """)
    List<ProductPricePeak> selectMaxPricesSince(@Param("productIds") List<Long> productIds,
                                                @Param("since") LocalDateTime since);
}
