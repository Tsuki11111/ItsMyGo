package com.tsuk1.itsmygo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tsuk1.itsmygo.entity.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    /** 锁定当前活跃管理员行；用于并发禁用时的「最后管理员」保护。 */
    @Select("SELECT id FROM users WHERE role = 'ADMIN' AND status = 1 FOR UPDATE")
    List<Long> selectActiveAdminIdsForUpdate();
}