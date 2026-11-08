package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 当前管理员的子角色与权限码（前端菜单/按钮按它渲染）。 */
public record PermissionView(List<String> roles, List<String> permissions) {
}
