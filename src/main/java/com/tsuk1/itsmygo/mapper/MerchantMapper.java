package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.Merchant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MerchantMapper extends BaseMapper<Merchant> {

    /** 审核通过：只有 PENDING 能变 ACTIVE，防并发重复审核 */
    @Update("""
            UPDATE merchants
            SET status = 'ACTIVE', reject_reason = ''
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int approveIfPending(@Param("id") Long id);

    @Update("""
            UPDATE merchants
            SET status = 'REJECTED', reject_reason = #{reason}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int rejectIfPending(@Param("id") Long id, @Param("reason") String reason);

    @Update("""
            UPDATE merchants SET status = 'DISABLED'
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int disableIfActive(@Param("id") Long id);

    @Update("""
            UPDATE merchants
            SET status = 'ACTIVE', reject_reason = ''
            WHERE id = #{id} AND status = 'DISABLED'
            """)
    int enableIfDisabled(@Param("id") Long id);
}
