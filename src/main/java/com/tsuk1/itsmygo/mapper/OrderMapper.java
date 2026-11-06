package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 原子状态流转 —— **把「检查状态」和「改状态」合成一条 SQL**。
     *
     * 原先的做法是「selectById 读出来 → 判断 canTransitionTo → updateById 写回」，
     * 两步之间有窗口：两个并发的支付请求会双双读到 PENDING_PAYMENT、双双通过检查，
     * 结果生成两条支付流水。加了这个条件更新之后，第二个请求影响行数为 0，被拒。
     *
     * 返回 0 表示「订单当前状态不是 expected」——要么被人抢先改过了，要么状态本就不对。
     */
    @Update("""
            UPDATE orders
            SET status = #{target}, updated_at = NOW()
            WHERE id = #{id} AND status = #{expected}
            """)
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target);

    /** 申请售后时锁订单行：同一订单的并发申请串行化，避免同一订单项被超量退。 */
    @Select("SELECT * FROM orders WHERE id = #{id} FOR UPDATE")
    Order selectByIdForUpdate(@Param("id") Long id);

    /** 累加已退金额（售后执行退款时调用）。 */
    @Update("""
            UPDATE orders
            SET refunded_amount = refunded_amount + #{amount}, updated_at = NOW()
            WHERE id = #{id}
            """)
    int addRefundedAmount(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);

    /** CREATING 超时扫描：找出超过指定秒数还没补账完成的占位订单。 */
    @Select("""
            SELECT * FROM orders
            WHERE status = 'CREATING'
              AND created_at <= DATE_SUB(NOW(), INTERVAL #{seconds} SECOND)
            ORDER BY id
            LIMIT #{limit}
            """)
    java.util.List<Order> selectCreatingBefore(@Param("seconds") int seconds,
                                               @Param("limit") int limit);

    /** CREATING 超时重投计数（同时作为扫描去重：只有 status 仍是 CREATING 才加）。 */
    @Update("""
            UPDATE orders
            SET create_attempts = create_attempts + 1, updated_at = NOW()
            WHERE id = #{id} AND status = 'CREATING'
            """)
    int incrementCreateAttempts(@Param("id") Long id);
}
