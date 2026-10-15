package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.LoginResponse;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 按 IP 限流：防批量注册 */
    @RateLimit(scene = "register", limit = 5, windowSeconds = 60, byIp = true)
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 按 IP 限流：防密码暴力破解（匿名接口，没有 userId 可用） */
    @RateLimit(scene = "login", limit = 10, windowSeconds = 60, byIp = true)
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 需要认证：回显 token 解析出的身份，用于前端校验登录态 */
    @GetMapping("/me")
    public Result<CurrentUser> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return Result.ok(currentUser);
    }
}
