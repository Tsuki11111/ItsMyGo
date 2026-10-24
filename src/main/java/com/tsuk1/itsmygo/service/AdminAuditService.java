package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AdminAuditView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.AdminAuditLog;

import java.time.LocalDate;

public interface AdminAuditService {

    void record(AdminAuditLog log);

    PageResponse<AdminAuditView> page(int page, int size, String keyword, String action,
                                      String result, LocalDate dateFrom, LocalDate dateTo);
}
