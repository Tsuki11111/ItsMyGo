package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Order;
import org.apache.ibatis.annotations.Param;
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
}
