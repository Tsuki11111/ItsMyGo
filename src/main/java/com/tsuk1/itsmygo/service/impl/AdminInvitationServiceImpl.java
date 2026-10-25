package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.admin.AdminGovernanceProperties;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.dto.AdminInvitationView;
import com.tsuk1.itsmygo.entity.AdminAuditLog;
import com.tsuk1.itsmygo.entity.AdminInvitation;
import com.tsuk1.itsmygo.entity.AdminInvitationStatus;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserStatus;
import com.tsuk1.itsmygo.mapper.AdminInvitationMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.security.AuthIdentityService;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AdminAuditService;
import com.tsuk1.itsmygo.service.AdminInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员邀请：发起 → 另一位管理员批准 → 被邀请人接受。
 *
 * 管理员不是自助注册出来的；邀请令牌只存 SHA-256，明文只在创建响应里出现一次。
 */
@Slf4j
@Service
public class AdminInvitationServiceImpl implements AdminInvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LIST_LIMIT = 200;

    private final AdminInvitationMapper invitationMapper;
    private final UserMapper userMapper;
    private final AuthIdentityService authIdentityService;
    private final AdminAuditService auditService;
    private final AdminGovernanceProperties properties;

    public AdminInvitationServiceImpl(AdminInvitationMapper invitationMapper, UserMapper userMapper,
                                      AuthIdentityService authIdentityService,
                                      AdminAuditService auditService,
                                      AdminGovernanceProperties properties) {
        this.invitationMapper = invitationMapper;
        this.userMapper = userMapper;
        this.authIdentityService = authIdentityService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public AdminInvitationView create(CurrentUser actor, String username) {
        String name = username == null ? "" : username.trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入要邀请的用户名");
        }
        User target = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, name)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!Roles.CUSTOMER.equals(target.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只能邀请普通顾客账号");
        }
        if (!Integer.valueOf(UserStatus.ACTIVE).equals(target.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该用户已被禁用");
        }

        expireStaleForTarget(target.getId());
        Long active = invitationMapper.selectCount(Wrappers.<AdminInvitation>lambdaQuery()
                .eq(AdminInvitation::getTargetUserId, target.getId())
                .in(AdminInvitation::getStatus,
                        AdminInvitationStatus.PENDING_APPROVAL, AdminInvitationStatus.APPROVED));
        if (active != null && active > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该用户已有待处理的管理员邀请");
        }

        String rawToken = newToken();
        AdminInvitation invitation = new AdminInvitation();
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setTargetUserId(target.getId());
        invitation.setInvitedBy(actor.id());
        invitation.setStatus(AdminInvitationStatus.PENDING_APPROVAL);
        invitation.setExpiresAt(LocalDateTime.now()
                .plusHours(properties.inviteExpiryHours()));
        invitationMapper.insert(invitation);

        Map<Long, User> users = usersOf(invitation);
        return toView(invitation, users, rawToken);
    }

    @Override
    public List<AdminInvitationView> list(String status) {
        List<AdminInvitation> invitations = invitationMapper.selectList(
                Wrappers.<AdminInvitation>lambdaQuery()
                        .eq(StringUtils.hasText(status), AdminInvitation::getStatus, status)
                        .orderByDesc(AdminInvitation::getId)
                        .last("LIMIT " + LIST_LIMIT));
        boolean changed = false;
        for (AdminInvitation invitation : invitations) {
            if (expireIfNeeded(invitation)) {
                changed = true;
            }
        }
        if (changed) {
            // 状态已经写回，重新读一遍保证返回值一致（也避免调用方看到旧对象）
            invitations = invitationMapper.selectList(Wrappers.<AdminInvitation>lambdaQuery()
                    .eq(StringUtils.hasText(status), AdminInvitation::getStatus, status)
                    .orderByDesc(AdminInvitation::getId)
                    .last("LIMIT " + LIST_LIMIT));
        }
        Map<Long, User> users = usersOf(invitations);
        return invitations.stream().map(item -> toView(item, users, null)).toList();
    }

    @Override
    @Transactional
    public AdminInvitationView approve(CurrentUser actor, Long id) {
        AdminInvitation invitation = require(id);
        if (expireIfNeeded(invitation)) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请已过期");
        }
        if (!AdminInvitationStatus.PENDING_APPROVAL.equals(invitation.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请不在待批准状态");
        }
        if (actor.id().equals(invitation.getInvitedBy())) {
            throw new BizException(ResultCode.BAD_REQUEST, "需要另一位管理员批准");
        }
        invitation.setStatus(AdminInvitationStatus.APPROVED);
        invitation.setApprovedBy(actor.id());
        invitation.setApprovedAt(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        return toView(invitation, usersOf(invitation), null);
    }

    @Override
    @Transactional
    public AdminInvitationView revoke(CurrentUser actor, Long id) {
        AdminInvitation invitation = require(id);
        if (!AdminInvitationStatus.PENDING_APPROVAL.equals(invitation.getStatus())
                && !AdminInvitationStatus.APPROVED.equals(invitation.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该邀请不能撤回");
        }
        invitation.setStatus(AdminInvitationStatus.REVOKED);
        invitationMapper.updateById(invitation);
        return toView(invitation, usersOf(invitation), null);
    }

    @Override
    @Transactional
    public void accept(CurrentUser user, String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请令牌不能为空");
        }
        AdminInvitation invitation = invitationMapper.selectOne(
                Wrappers.<AdminInvitation>lambdaQuery()
                        .eq(AdminInvitation::getTokenHash, hashToken(token.trim()))
                        .last("LIMIT 1"));
        if (invitation == null || !user.id().equals(invitation.getTargetUserId())) {
            // 不泄露「这个 token 是否存在、属于谁」
            throw new BizException(ResultCode.NOT_FOUND, "邀请不存在或已失效");
        }
        if (expireIfNeeded(invitation)) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请已过期");
        }
        if (AdminInvitationStatus.PENDING_APPROVAL.equals(invitation.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请尚未批准");
        }
        if (!AdminInvitationStatus.APPROVED.equals(invitation.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请已失效");
        }

        User target = userMapper.selectById(user.id());
        if (target == null || !Integer.valueOf(UserStatus.ACTIVE).equals(target.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "账号不可用");
        }
        target.setRole(Roles.ADMIN);
        userMapper.updateById(target);

        invitation.setStatus(AdminInvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationMapper.updateById(invitation);

        // 旧 JWT 里的 role 还是 CUSTOMER，必须重新登录；这里先把授权快照清掉
        authIdentityService.evict(user.id());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    authIdentityService.evict(user.id());
                    recordAcceptAudit(user, invitation);
                }
            });
        } else {
            recordAcceptAudit(user, invitation);
        }
    }

    private AdminInvitation require(Long id) {
        AdminInvitation invitation = invitationMapper.selectById(id);
        if (invitation == null) {
            throw new BizException(ResultCode.NOT_FOUND, "邀请不存在");
        }
        return invitation;
    }

    private void expireStaleForTarget(Long targetUserId) {
        List<AdminInvitation> stale = invitationMapper.selectList(
                Wrappers.<AdminInvitation>lambdaQuery()
                        .eq(AdminInvitation::getTargetUserId, targetUserId)
                        .in(AdminInvitation::getStatus,
                                AdminInvitationStatus.PENDING_APPROVAL, AdminInvitationStatus.APPROVED));
        for (AdminInvitation invitation : stale) {
            expireIfNeeded(invitation);
        }
    }

    private boolean expireIfNeeded(AdminInvitation invitation) {
        if (!AdminInvitationStatus.PENDING_APPROVAL.equals(invitation.getStatus())
                && !AdminInvitationStatus.APPROVED.equals(invitation.getStatus())) {
            return false;
        }
        if (invitation.getExpiresAt() == null
                || !invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        AdminInvitation update = new AdminInvitation();
        update.setId(invitation.getId());
        update.setStatus(AdminInvitationStatus.EXPIRED);
        invitationMapper.updateById(update);
        invitation.setStatus(AdminInvitationStatus.EXPIRED);
        return true;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "邀请令牌生成失败");
        }
    }

    private Map<Long, User> usersOf(AdminInvitation invitation) {
        return usersOf(List.of(invitation));
    }

    private Map<Long, User> usersOf(List<AdminInvitation> invitations) {
        Set<Long> ids = new HashSet<>();
        for (AdminInvitation invitation : invitations) {
            if (invitation.getTargetUserId() != null) {
                ids.add(invitation.getTargetUserId());
            }
            if (invitation.getInvitedBy() != null) {
                ids.add(invitation.getInvitedBy());
            }
            if (invitation.getApprovedBy() != null) {
                ids.add(invitation.getApprovedBy());
            }
        }
        Map<Long, User> users = new HashMap<>();
        if (ids.isEmpty()) {
            return users;
        }
        for (User user : userMapper.selectBatchIds(ids)) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private AdminInvitationView toView(AdminInvitation invitation, Map<Long, User> users,
                                       String token) {
        User target = users.get(invitation.getTargetUserId());
        User inviter = users.get(invitation.getInvitedBy());
        User approver = invitation.getApprovedBy() == null
                ? null : users.get(invitation.getApprovedBy());
        return new AdminInvitationView(
                invitation.getId(),
                invitation.getTargetUserId(),
                target == null ? "" : target.getUsername(),
                target == null ? "" : target.getNickname(),
                invitation.getInvitedBy(),
                inviter == null ? "" : inviter.getUsername(),
                invitation.getApprovedBy(),
                approver == null ? "" : approver.getUsername(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getApprovedAt(),
                invitation.getAcceptedAt(),
                invitation.getCreatedAt(),
                token);
    }

    /** 接受邀请是用户动作，不在 Admin*Controller 的 AOP 切面里，这里手动补一条授权审计。 */
    private void recordAcceptAudit(CurrentUser user, AdminInvitation invitation) {
        try {
            AdminAuditLog audit = new AdminAuditLog();
            audit.setActorUserId(user.id());
            audit.setActorUsername(user.username());
            audit.setAction("ADMIN_INVITE_ACCEPT");
            audit.setTargetType("USER");
            audit.setTargetId(user.id());
            audit.setSummary("接受管理员邀请");
            audit.setResult("SUCCESS");
            audit.setErrorMessage("");
            audit.setTraceId(MDC.get(RequestIdFilter.TRACE_ID));
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                audit.setIp(request.getRemoteAddr());
                audit.setUserAgent(request.getHeader("User-Agent"));
            } else {
                audit.setIp("");
                audit.setUserAgent("");
            }
            audit.setDetail("invitationId=" + invitation.getId()
                    + "; invitedBy=" + invitation.getInvitedBy()
                    + "; approvedBy=" + invitation.getApprovedBy());
            audit.setCreatedAt(LocalDateTime.now());
            auditService.record(audit);
        } catch (Exception e) {
            log.warn("记录管理员邀请接受审计失败：invitationId={}", invitation.getId(), e);
        }
    }
}
