package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.ChangePasswordRequest;
import com.tsuk1.itsmygo.dto.ProfileView;
import com.tsuk1.itsmygo.dto.UpdateProfileRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户中心：个人资料与密码。
 *
 * 权限走 SecurityConfig 的 `anyRequest().authenticated()` —— 顾客登录即可，
 * 不需要像管理端那样单独配角色。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public Result<ProfileView> profile(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(userService.profile(user.id()));
    }

    @PutMapping("/profile")
    public Result<ProfileView> update(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(user.id(), request));
    }

    /** 改完密码前端会主动退出登录；服务端不吊销 token（JWT 无状态） */
    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
        return Result.ok();
    }

    /** 按用户限流：头像是往对象存储写东西，别让人刷 */
    @RateLimit(scene = "avatar", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/avatar")
    public Result<ProfileView> avatar(@AuthenticationPrincipal CurrentUser user,
                                      @RequestParam("file") MultipartFile file) {
        return Result.ok(userService.updateAvatar(user.id(), file));
    }
}
