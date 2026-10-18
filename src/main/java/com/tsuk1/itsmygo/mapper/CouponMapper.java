package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 领取时占用一个名额 —— 判断与自增在同一条 SQL 里。
     * 返回 0 表示已领完/已停用/已过期，调用方应回滚刚插入的用户券。
     */
    @Update("""
            UPDATE coupons
            SET claimed_count = claimed_count + 1
            WHERE id = #{id}
              AND status = 1
              AND claimed_count < total_count
              AND valid_from <= NOW()
              AND valid_to >= NOW()
            """)
    int claimOne(@Param("id") Long id);
}
