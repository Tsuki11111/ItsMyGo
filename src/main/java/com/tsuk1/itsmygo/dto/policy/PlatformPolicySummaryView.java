package com.tsuk1.itsmygo.dto.policy;

import java.time.LocalDateTime;

/** 政策列表项：不返回全文，避免管理端首屏过大。 */
public record PlatformPolicySummaryView(
        String policyKey,
        String title,
        String category,
        String version,
        long sectionCount,
        LocalDateTime updatedAt) {
}
