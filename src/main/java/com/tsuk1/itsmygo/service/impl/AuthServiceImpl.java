package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.LoginResponse;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.security.JwtService;
import com.tsuk1.itsmygo.service.AuthService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper userMapper, MerchantMapper merchantMapper,
                           PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setPhone("");
        user.setStatus(1);
        // 注册一律是顾客；管理员由数据库直接授予，不开放自助注册
        user.setRole(Roles.CUSTOMER);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 靠 users.uk_username 兜底，而不是「先查再插」——那中间有竞态窗口
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已被占用");
        }
        return toResponse(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()));

        // 用户不存在与密码错误给同一句提示，不泄露账号是否已注册
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        return toResponse(user);
    }

    private LoginResponse toResponse(User user) {
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole());
        Merchant merchant = user.getMerchantId() == null ? null
                : merchantMapper.selectById(user.getMerchantId());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatar() == null ? "" : user.getAvatar(), user.getRole(),
                user.getMerchantId(),
                merchant == null ? "" : merchant.getName(),
                merchant == null ? "" : merchant.getStatus());
    }
}
