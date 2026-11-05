package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AfterSaleItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AfterSaleItemMapper extends BaseMapper<AfterSaleItem> {

    /**
     * 某个订单项「已占用」的退款数量（申请中 + 待退货 + 退货中 + 已退款）。
     * 用来拦住「同一订单项重复退超量」。
     */
    @Select("""
            SELECT COALESCE(SUM(asi.quantity), 0) FROM after_sale_items asi
            JOIN after_sales a ON a.id = asi.after_sale_id
            WHERE asi.order_item_id = #{orderItemId}
              AND a.status IN ('PENDING_REVIEW', 'WAITING_RETURN', 'RETURN_SHIPPED', 'REFUNDED')
            """)
    int occupiedQuantity(@Param("orderItemId") Long orderItemId);
}
