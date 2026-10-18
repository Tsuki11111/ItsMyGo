package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ChangePasswordRequest;
import com.tsuk1.itsmygo.dto.ProfileView;
import com.tsuk1.itsmygo.dto.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

/** 用户中心：个人资料与密码 */
public interface UserService {

    ProfileView profile(Long userId);

    ProfileView updateProfile(Long userId, UpdateProfileRequest request);

    /** 校验旧密码后改密。**不会动登录态** —— JWT 是无状态的，前端负责改完重新登录 */
    void changePassword(Long userId, ChangePasswordRequest request);

    ProfileView updateAvatar(Long userId, MultipartFile file);
}
