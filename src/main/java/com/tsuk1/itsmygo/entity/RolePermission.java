package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色-权限关联。 */
@Data
@TableName("role_permissions")
public class RolePermission {

    private Long roleId;

    private Long permissionId;
}
