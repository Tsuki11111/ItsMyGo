package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.dto.AdminUserView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AdminUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端用户管理：列表与禁用 / 启用。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Result<PageResponse<AdminUserView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminUserService.page(page, size, keyword, role, status));
    }

    @PostMapping("/{id}/disable")
    @AdminAudit(action = "USER_DISABLE", label = "禁用用户", targetType = "USER")
    public Result<Void> disable(@AuthenticationPrincipal CurrentUser user,
                                @PathVariable Long id) {
        adminUserService.disable(user.id(), id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/enable")
    @AdminAudit(action = "USER_ENABLE", label = "启用用户", targetType = "USER")
    public Result<Void> enable(@AuthenticationPrincipal CurrentUser user,
                               @PathVariable Long id) {
        adminUserService.enable(user.id(), id);
        return Result.ok(null);
    }
}
