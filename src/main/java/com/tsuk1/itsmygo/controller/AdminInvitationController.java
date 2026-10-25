package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.AdminInvitationCreateRequest;
import com.tsuk1.itsmygo.dto.AdminInvitationView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AdminInvitationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端：管理员邀请的发起、批准与撤回。 */
@RestController
@RequestMapping("/api/admin/invitations")
public class AdminInvitationController {

    private final AdminInvitationService invitationService;

    public AdminInvitationController(AdminInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** 创建后只在响应里返回一次明文 token，列表接口不会返回 */
    @PostMapping
    @AdminAudit(action = "ADMIN_INVITE_CREATE", label = "发起管理员邀请",
            targetType = "ADMIN_INVITATION")
    public Result<AdminInvitationView> create(@AuthenticationPrincipal CurrentUser actor,
                                              @Valid @RequestBody AdminInvitationCreateRequest request) {
        return Result.ok(invitationService.create(actor, request.username()));
    }

    @GetMapping
    public Result<List<AdminInvitationView>> list(
            @RequestParam(required = false) String status) {
        return Result.ok(invitationService.list(status));
    }

    @PostMapping("/{id}/approve")
    @AdminAudit(action = "ADMIN_INVITE_APPROVE", label = "批准管理员邀请",
            targetType = "ADMIN_INVITATION")
    public Result<AdminInvitationView> approve(@AuthenticationPrincipal CurrentUser actor,
                                               @PathVariable Long id) {
        return Result.ok(invitationService.approve(actor, id));
    }

    @PostMapping("/{id}/revoke")
    @AdminAudit(action = "ADMIN_INVITE_REVOKE", label = "撤回管理员邀请",
            targetType = "ADMIN_INVITATION")
    public Result<AdminInvitationView> revoke(@AuthenticationPrincipal CurrentUser actor,
                                              @PathVariable Long id) {
        return Result.ok(invitationService.revoke(actor, id));
    }
}
