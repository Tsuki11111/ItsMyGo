package com.tsuk1.itsmygo.common.rbac;

import java.util.Set;

/**
 * 权限码读取端口（shared kernel）。
 *
 * <p>安全层只依赖本接口，不反向依赖 service 层；由 RbacService 实现。</p>
 */
public interface PermissionReader {

    Set<String> permissionCodes(Long adminUserId);
}
