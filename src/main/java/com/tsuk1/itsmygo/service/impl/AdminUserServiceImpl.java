package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdminUserView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserStatus;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.security.AuthIdentityService;
import com.tsuk1.itsmygo.service.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final AuthIdentityService authIdentityService;

    public AdminUserServiceImpl(UserMapper userMapper, AuthIdentityService authIdentityService) {
        this.userMapper = userMapper;
        this.authIdentityService = authIdentityService;
    }

    @Override
    public PageResponse<AdminUserView> page(int page, int size, String keyword, String role,
                                             Integer status) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<User> result = userMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)),
                Wrappers.<User>lambdaQuery()
                        .select(User::getId, User::getUsername, User::getNickname, User::getPhone,
                                User::getAvatar, User::getStatus, User::getRole,
                                User::getMerchantId, User::getCreatedAt, User::getUpdatedAt)
                        .eq(StringUtils.hasText(role), User::getRole, role)
                        .eq(status != null, User::getStatus, status)
                        .and(kw != null, wrapper -> wrapper
                                .like(User::getUsername, kw)
                                .or().like(User::getNickname, kw)
                                .or().like(User::getPhone, kw))
                        .orderByDesc(User::getId));
        List<AdminUserView> views = result.getRecords().stream()
                .map(AdminUserView::from)
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public void disable(Long actorId, Long userId) {
        changeStatus(actorId, userId, UserStatus.DISABLED);
    }

    @Override
    @Transactional
    public void enable(Long actorId, Long userId) {
        changeStatus(actorId, userId, UserStatus.ACTIVE);
    }

    private void changeStatus(Long actorId, Long userId, int targetStatus) {
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (targetStatus == UserStatus.DISABLED && actorId.equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能禁用当前登录账号");
        }
        if (targetStatus == UserStatus.DISABLED
                && Roles.ADMIN.equals(target.getRole())
                && Integer.valueOf(UserStatus.ACTIVE).equals(target.getStatus())) {
            // 锁住活跃管理员，避免两个并发禁用请求同时通过「只剩一个」检查
            List<Long> activeAdmins = userMapper.selectActiveAdminIdsForUpdate();
            if (activeAdmins.size() <= 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "不能禁用最后一个管理员");
            }
        }
        if (Integer.valueOf(targetStatus).equals(target.getStatus())) {
            return;
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(targetStatus);
        userMapper.updateById(update);

        // 立即失效 + 提交后再次失效：避免提交前并发请求把旧快照写回缓存
        authIdentityService.evict(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    authIdentityService.evict(userId);
                }
            });
        }
    }
}
