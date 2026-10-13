package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.LoginResponse;
import com.tsuk1.itsmygo.dto.RegisterRequest;

public interface AuthService {

    /** 注册并直接签发 token */
    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
