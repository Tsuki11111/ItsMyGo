package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.entity.UserCoupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 下单时把券置为已使用 —— 条件更新是「一张券只能用一次」的最终防线。
     * 返回 0 表示券不属于该用户 / 已被用 / 已过期。
     */
    @Update("""
            UPDATE user_coupons
            SET status = 'USED', used_order_id = #{orderId}, used_at = NOW()
            WHERE id = #{id}
              AND user_id = #{userId}
              AND status = 'UNUSED'
              AND valid_from <= NOW()
              AND valid_to >= NOW()
            """)
    int markUsed(@Param("id") Long id, @Param("userId") Long userId,
                 @Param("orderId") Long orderId);

    /**
     * 取消/退款时退券：未过期恢复 UNUSED，已过期置 EXPIRED。
     * `WHERE used_order_id=? AND status='USED'` 保证重复调用幂等。
     */
    @Update("""
            UPDATE user_coupons
            SET status = IF(valid_to >= NOW(), 'UNUSED', 'EXPIRED'),
                used_order_id = NULL,
                used_at = NULL
            WHERE used_order_id = #{orderId} AND status = 'USED'
            """)
    int restoreByOrderId(@Param("orderId") Long orderId);

    /**
     * 我的券分页。展示状态在 SQL 里按数据库 NOW() 计算 ——
     * 不用 JVM 的 now()，避免应用与数据库时区不一致（HANDOFF §5.14）。
     */
    @Select("""
            <script>
            SELECT uc.*,
                   CASE WHEN uc.status = 'UNUSED' AND uc.valid_to &lt; NOW()
                        THEN 'EXPIRED' ELSE uc.status END AS display_status
            FROM user_coupons uc
            WHERE uc.user_id = #{userId}
            <if test="status != null and status != ''">
              <choose>
                <when test="status == 'UNUSED'">
                  AND uc.status = 'UNUSED' AND uc.valid_to &gt;= NOW()
                </when>
                <when test="status == 'EXPIRED'">
                  AND (uc.status = 'EXPIRED'
                       OR (uc.status = 'UNUSED' AND uc.valid_to &lt; NOW()))
                </when>
                <otherwise>
                  AND uc.status = #{status}
                </otherwise>
              </choose>
            </if>
            ORDER BY uc.id DESC
            </script>
            """)
    Page<UserCoupon> selectMinePage(Page<UserCoupon> page,
                                    @Param("userId") Long userId,
                                    @Param("status") String status);

    /** 结算页预校验：未使用、未过期、门槛满足；按减免从大到小 */
    @Select("""
            <script>
            SELECT * FROM user_coupons
            WHERE user_id = #{userId}
              AND status = 'UNUSED'
              AND valid_from &lt;= NOW()
              AND valid_to &gt;= NOW()
              AND threshold_amount &lt;= #{amount}
            <if test="merchantId != null">
              AND (merchant_id IS NULL OR merchant_id = #{merchantId})
            </if>
            ORDER BY discount_amount DESC, id DESC
            </script>
            """)
    List<UserCoupon> selectAvailable(@Param("userId") Long userId,
                                     @Param("amount") BigDecimal amount,
                                     @Param("merchantId") Long merchantId);
}
