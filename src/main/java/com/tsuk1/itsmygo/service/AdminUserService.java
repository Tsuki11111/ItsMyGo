package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AdminUserView;
import com.tsuk1.itsmygo.dto.PageResponse;

public interface AdminUserService {

    PageResponse<AdminUserView> page(int page, int size, String keyword, String role,
                                     Integer status);

    void disable(Long actorId, Long userId);

    void enable(Long actorId, Long userId);
}
