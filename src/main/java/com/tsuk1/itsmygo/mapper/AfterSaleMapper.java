package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.AfterSale;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    /**
     * 原子状态流转（与订单状态机同一套路）：把「判断」和「改」合成一条 SQL。
     * 返回 0 = 状态已被别人改过，调用方应拒绝。
     */
    @Update("""
            UPDATE after_sales
            SET status = #{target}, updated_at = NOW()
            WHERE id = #{id} AND status = #{expected}
            """)
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target);

    /** 商家审核超时：PENDING_REVIEW 且已过 review_deadline。 */
    @Select("""
            SELECT * FROM after_sales
            WHERE status = 'PENDING_REVIEW'
              AND review_deadline IS NOT NULL
              AND review_deadline <= NOW()
            ORDER BY review_deadline
            LIMIT #{limit}
            """)
    List<AfterSale> selectReviewExpired(@Param("limit") int limit);

    /** 退货超时：RETURN_SHIPPED 且买家寄出超过 N 天，商家仍未确认收货。 */
    @Select("""
            SELECT * FROM after_sales
            WHERE status = 'RETURN_SHIPPED'
              AND shipped_back_at IS NOT NULL
              AND shipped_back_at <= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            ORDER BY shipped_back_at
            LIMIT #{limit}
            """)
    List<AfterSale> selectReturnConfirmExpired(@Param("days") int days, @Param("limit") int limit);

    /** 按状态聚合（商家看板 / 管理端概览用）。 */
    @Select("""
            SELECT status, COUNT(*) AS cnt FROM after_sales
            WHERE merchant_id = #{merchantId}
            GROUP BY status
            """)
    List<Map<String, Object>> countByMerchant(@Param("merchantId") Long merchantId);

    @Select("SELECT status, COUNT(*) AS cnt FROM after_sales GROUP BY status")
    List<Map<String, Object>> countAll();
}
