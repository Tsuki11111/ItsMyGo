package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.common.rbac.PermissionReader;

import java.util.List;
import java.util.Set;

/** 管理端 RBAC：角色/权限码加载与缓存。 */
public interface RbacService extends PermissionReader {

    boolean enabled();

    /** 管理员拥有的权限码；rbac 关闭或未分配角色（历史管理员）时返回全部权限码 */
    Set<String> permissionCodes(Long adminUserId);

    /** 管理员子角色 code（SUPER_ADMIN / OPERATOR / ...） */
    List<String> roleCodes(Long adminUserId);

    /** 角色/权限变更后清理缓存 */
    void evict(Long adminUserId);
}
