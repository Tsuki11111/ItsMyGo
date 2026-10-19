package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ChangePasswordRequest;
import com.tsuk1.itsmygo.dto.ProfileView;
import com.tsuk1.itsmygo.dto.UpdateProfileRequest;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.StorageService;
import com.tsuk1.itsmygo.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    public UserServiceImpl(UserMapper userMapper, MerchantMapper merchantMapper,
                           PasswordEncoder passwordEncoder, StorageService storageService) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    @Override
    public ProfileView profile(Long userId) {
        return toView(requireUser(userId));
    }

    @Override
    public ProfileView updateProfile(Long userId, UpdateProfileRequest request) {
        requireUser(userId);
        // 只写要改的字段：整条 updateById 会把查出来那一刻的昵称/手机号一并写回，
        // 覆盖掉这期间别处改过的值
        User update = new User();
        update.setId(userId);
        update.setNickname(request.nickname());
        update.setPhone(request.phone() == null ? "" : request.phone());
        userMapper.updateById(update);
        return profile(userId);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = requireUser(userId);

        // 旧密码错用 400 而不是 401 —— 前端拦截器把 401 当「登录失效」直接登出，
        // 用户只是输错一次旧密码，不该被踢出去
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "旧密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能和旧密码相同");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(update);
    }

    @Override
    public ProfileView updateAvatar(Long userId, MultipartFile file) {
        requireUser(userId);
        String url = storageService.uploadAvatar(file);

        User update = new User();
        update.setId(userId);
        update.setAvatar(url);
        userMapper.updateById(update);
        return profile(userId);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private ProfileView toView(User user) {
        Merchant merchant = user.getMerchantId() == null ? null
                : merchantMapper.selectById(user.getMerchantId());
        return new ProfileView(user.getId(), user.getUsername(), user.getNickname(),
                user.getPhone(), user.getAvatar() == null ? "" : user.getAvatar(), user.getRole(),
                user.getMerchantId(),
                merchant == null ? "" : merchant.getName(),
                merchant == null ? "" : merchant.getStatus());
    }
}
