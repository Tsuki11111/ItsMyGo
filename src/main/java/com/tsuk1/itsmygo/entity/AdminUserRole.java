package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 管理员-角色关联（仅 users.role=ADMIN 使用）。 */
@Data
@TableName("admin_user_roles")
public class AdminUserRole {

    private Long userId;

    private Long roleId;
}
