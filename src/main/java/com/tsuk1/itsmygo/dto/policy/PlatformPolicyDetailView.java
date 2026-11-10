package com.tsuk1.itsmygo.dto.policy;

import java.time.LocalDateTime;
import java.util.List;

/** 政策详情：管理端展示章节，agent 导入脚本也按这个结构读取。 */
public record PlatformPolicyDetailView(
        String policyKey,
        String title,
        String category,
        String version,
        LocalDateTime effectiveAt,
        LocalDateTime updatedAt,
        List<PlatformPolicySectionView> sections) {
}
