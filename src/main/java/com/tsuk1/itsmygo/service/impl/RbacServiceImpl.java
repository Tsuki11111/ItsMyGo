package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.rbac.RbacProperties;
import com.tsuk1.itsmygo.entity.AdminUserRole;
import com.tsuk1.itsmygo.entity.Permission;
import com.tsuk1.itsmygo.entity.Role;
import com.tsuk1.itsmygo.entity.RolePermission;
import com.tsuk1.itsmygo.mapper.AdminUserRoleMapper;
import com.tsuk1.itsmygo.mapper.PermissionMapper;
import com.tsuk1.itsmygo.mapper.RoleMapper;
import com.tsuk1.itsmygo.mapper.RolePermissionMapper;
import com.tsuk1.itsmygo.service.RbacService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** RBAC 实现：权限码来自 DB，Redis 缓存 60s；关闭开关时管理员拥有全部权限码。 */
@Service
public class RbacServiceImpl implements RbacService {

    private static final String PERM_PREFIX = "its-my-go:rbac:perm:";
    private static final String ROLE_PREFIX = "its-my-go:rbac:roles:";
    private static final String ALL_KEY = "its-my-go:rbac:all-permissions";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final AdminUserRoleMapper adminUserRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;
    private final RbacProperties properties;

    public RbacServiceImpl(AdminUserRoleMapper adminUserRoleMapper,
                           RoleMapper roleMapper,
                           RolePermissionMapper rolePermissionMapper,
                           PermissionMapper permissionMapper,
                           StringRedisTemplate redisTemplate,
                           RbacProperties properties) {
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public Set<String> permissionCodes(Long adminUserId) {
        if (adminUserId == null) {
            return Set.of();
        }
        if (!properties.isEnabled()) {
            return allCodes();
        }
        String cached = redisTemplate.opsForValue().get(PERM_PREFIX + adminUserId);
        if (cached != null) {
            return split(cached);
        }
        Set<String> codes = computePermissionCodes(adminUserId);
        redisTemplate.opsForValue().set(PERM_PREFIX + adminUserId, String.join(",", codes), TTL);
        return codes;
    }

    @Override
    public List<String> roleCodes(Long adminUserId) {
        if (adminUserId == null) {
            return List.of();
        }
        String cached = redisTemplate.opsForValue().get(ROLE_PREFIX + adminUserId);
        if (cached != null) {
            return cached.isEmpty() ? List.of() : Arrays.asList(cached.split(","));
        }
        List<Long> roleIds = roleIds(adminUserId);
        List<String> codes = roleIds.isEmpty() ? List.of()
                : roleMapper.selectBatchIds(roleIds).stream().map(Role::getCode).toList();
        redisTemplate.opsForValue().set(ROLE_PREFIX + adminUserId, String.join(",", codes), TTL);
        return codes;
    }

    @Override
    public void evict(Long adminUserId) {
        if (adminUserId == null) {
            return;
        }
        redisTemplate.delete(PERM_PREFIX + adminUserId);
        redisTemplate.delete(ROLE_PREFIX + adminUserId);
    }

    private Set<String> computePermissionCodes(Long adminUserId) {
        List<Long> roleIds = roleIds(adminUserId);
        // 历史管理员（V36 之前创建、没有角色行）按 SUPER_ADMIN 处理，避免升级后失联
        if (roleIds.isEmpty()) {
            return allCodes();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()))) {
            return allCodes();
        }
        List<RolePermission> links = rolePermissionMapper.selectList(
                Wrappers.<RolePermission>lambdaQuery().in(RolePermission::getRoleId, roleIds));
        Set<Long> permissionIds = links.stream().map(RolePermission::getPermissionId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Long> roleIds(Long adminUserId) {
        return adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery()
                        .eq(AdminUserRole::getUserId, adminUserId))
                .stream().map(AdminUserRole::getRoleId).toList();
    }

    private Set<String> allCodes() {
        String cached = redisTemplate.opsForValue().get(ALL_KEY);
        if (cached != null) {
            return split(cached);
        }
        Set<String> codes = permissionMapper.selectList(null).stream()
                .map(Permission::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        redisTemplate.opsForValue().set(ALL_KEY, String.join(",", codes), Duration.ofMinutes(5));
        return codes;
    }

    private Set<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
