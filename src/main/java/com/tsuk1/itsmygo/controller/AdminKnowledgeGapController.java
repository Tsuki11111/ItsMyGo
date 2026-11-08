package com.tsuk1.itsmygo.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.agent.KnowledgeGapClient;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapListView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapReportView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端知识缺口：代理 rag_helper 的 8001 管理接口。
 *
 * 浏览器只访问 Java；访问密钥只留在后端配置里。
 * 流程：列缺口 → 生成 FAQ 草稿（**不自动入库**）→ 人工核实 → 发布（走导入链路）→ 标记 resolved。
 */
@PreAuthorize("hasAuthority('admin:knowledge-gap:manage')")
@RestController
@RequestMapping("/api/admin/knowledge-gaps")
public class AdminKnowledgeGapController {

    private final KnowledgeGapClient gapClient;

    public AdminKnowledgeGapController(KnowledgeGapClient gapClient) {
        this.gapClient = gapClient;
    }

    /** 缺口列表：按频次降序，含产品归因与状态；不调模型 */
    @GetMapping
    public Result<KnowledgeGapListView> list(
            @RequestParam(defaultValue = "7") double days,
            @RequestParam(defaultValue = "1") int minCount) {
        return Result.ok(gapClient.list(clampDays(days), clampMinCount(minCount)));
    }

    /** 缺口周报（Markdown） */
    @GetMapping("/report")
    public Result<KnowledgeGapReportView> report(
            @RequestParam(defaultValue = "7") double days) {
        return Result.ok(gapClient.report(clampDays(days)));
    }

    /** 生成 FAQ 草稿：只返回文本，不入库；人工核实后才发布 */
    @RateLimit(scene = "knowledge-gap-draft", limit = 5, windowSeconds = 60, byUser = true)
    @PostMapping("/draft")
    @AdminAudit(action = "KNOWLEDGE_GAP_DRAFT", label = "生成知识缺口草稿",
            targetType = "KNOWLEDGE_GAP")
    public Result<KnowledgeGapDraftView> draft(
            @Valid @RequestBody KnowledgeGapDraftRequest request) {
        return Result.ok(gapClient.draft(request));
    }

    /** 回写缺口状态：resolved / ignored / published / pending */
    @PostMapping("/status")
    @AdminAudit(action = "KNOWLEDGE_GAP_STATUS", label = "更新知识缺口状态",
            targetType = "KNOWLEDGE_GAP")
    public Result<KnowledgeGapStatusView> updateStatus(
            @Valid @RequestBody KnowledgeGapStatusRequest request) {
        return Result.ok(gapClient.updateStatus(request));
    }

    /** 发布草稿：交给 rag_helper 导入链路，返回 taskId 供平台轮询进度 */
    @RateLimit(scene = "knowledge-gap-publish", limit = 3, windowSeconds = 60, byUser = true)
    @PostMapping("/publish")
    @AdminAudit(action = "KNOWLEDGE_GAP_PUBLISH", label = "发布知识缺口草稿",
            targetType = "KNOWLEDGE_GAP")
    public Result<KnowledgeGapPublishView> publish(
            @Valid @RequestBody KnowledgeGapPublishRequest request) {
        return Result.ok(gapClient.publish(request));
    }

    private static double clampDays(double days) {
        return Math.max(1.0, Math.min(days, 90.0));
    }

    private static int clampMinCount(int minCount) {
        return Math.max(1, Math.min(minCount, 1000));
    }
}
