package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.dto.AdminAuditView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.entity.AdminAuditLog;
import com.tsuk1.itsmygo.mapper.AdminAuditLogMapper;
import com.tsuk1.itsmygo.service.AdminAuditService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminAuditServiceImpl implements AdminAuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditLogMapper auditLogMapper;

    public AdminAuditServiceImpl(AdminAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void record(AdminAuditLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        auditLogMapper.insert(log);
    }

    @Override
    public PageResponse<AdminAuditView> page(int page, int size, String keyword, String action,
                                              String result, LocalDate dateFrom, LocalDate dateTo) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<AdminAuditLog> pageResult = auditLogMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)),
                Wrappers.<AdminAuditLog>lambdaQuery()
                        .eq(StringUtils.hasText(action), AdminAuditLog::getAction, action)
                        .eq(StringUtils.hasText(result), AdminAuditLog::getResult, result)
                        .ge(dateFrom != null, AdminAuditLog::getCreatedAt,
                                dateFrom == null ? null : dateFrom.atStartOfDay())
                        .lt(dateTo != null, AdminAuditLog::getCreatedAt,
                                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay())
                        .and(kw != null, wrapper -> wrapper
                                .like(AdminAuditLog::getActorUsername, kw)
                                .or().like(AdminAuditLog::getSummary, kw)
                                .or().like(AdminAuditLog::getTraceId, kw)
                                .or().like(AdminAuditLog::getIp, kw)
                                .or().apply("CAST(target_id AS CHAR) LIKE {0}", "%" + kw + "%"))
                        .orderByDesc(AdminAuditLog::getCreatedAt)
                        .orderByDesc(AdminAuditLog::getId));
        List<AdminAuditView> views = pageResult.getRecords().stream()
                .map(AdminAuditView::from)
                .toList();
        return new PageResponse<>(views, pageResult.getTotal(),
                pageResult.getCurrent(), pageResult.getSize());
    }
}
