package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.policy.PlatformPolicyDetailView;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicySummaryView;

import java.util.List;

/** 平台政策只读查询：政策原文与章节以 MySQL 为准。 */
public interface PlatformPolicyService {

    List<PlatformPolicySummaryView> list();

    PlatformPolicyDetailView get(String policyKey);

    /** 管理端上传 Markdown：覆盖该政策原文与章节，向量同步由 rag_helper 负责。 */
    void importMarkdown(String policyKey, String markdown);
}
