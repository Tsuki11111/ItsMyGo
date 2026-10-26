package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.agent.AgentEvalClient;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.eval.AgentEvalCasesView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalCaseCreateRequest;
import com.tsuk1.itsmygo.dto.eval.AgentEvalJobListView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalJobView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalResultListView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalResultView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalRunRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端智能体评测：代理 rag_helper 的 8002 评测接口。
 * 浏览器只访问 Java；访问密钥只留在后端配置里。
 */
@RestController
@RequestMapping("/api/admin/agent-eval")
public class AdminAgentEvalController {

    private final AgentEvalClient evalClient;

    public AdminAgentEvalController(AgentEvalClient evalClient) {
        this.evalClient = evalClient;
    }

    @GetMapping("/cases")
    public Result<AgentEvalCasesView> cases() {
        return Result.ok(evalClient.cases());
    }

    /** 新建自建用例：写到 rag_helper 持久化目录，立即出现在用例列表。 */
    @RateLimit(scene = "agent-eval-case", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/cases")
    @AdminAudit(action = "AGENT_EVAL_CASE_CREATE", label = "新建智能体评测用例",
            targetType = "AGENT_EVAL")
    public Result<AgentEvalCasesView.AgentEvalCaseView> createCase(
            @Valid @RequestBody AgentEvalCaseCreateRequest request) {
        return Result.ok(evalClient.createCase(request));
    }

    @GetMapping("/results")
    public Result<AgentEvalResultListView> results(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(new AgentEvalResultListView(
                evalClient.results(clamp(limit, 1, 100))));
    }

    @GetMapping("/results/{name}")
    public Result<AgentEvalResultView> result(@PathVariable String name) {
        return Result.ok(evalClient.result(name));
    }

    /** 启动后台评测；真正执行在 rag_helper，Java 只负责鉴权和转发。 */
    @RateLimit(scene = "agent-eval-run", limit = 3, windowSeconds = 60, byUser = true)
    @PostMapping("/runs")
    @AdminAudit(action = "AGENT_EVAL_RUN", label = "启动智能体评测",
            targetType = "AGENT_EVAL")
    public Result<AgentEvalJobView> run(@Valid @RequestBody AgentEvalRunRequest request) {
        return Result.ok(evalClient.start(request));
    }

    @GetMapping("/jobs")
    public Result<AgentEvalJobListView> jobs(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(new AgentEvalJobListView(
                evalClient.jobs(clamp(limit, 1, 20))));
    }

    @GetMapping("/jobs/{id}")
    public Result<AgentEvalJobView> job(@PathVariable String id) {
        return Result.ok(evalClient.job(id));
    }

    /** 协作式停止：当前用例跑完后退出，已产出的结果保留。 */
    @PostMapping("/jobs/{id}/stop")
    @AdminAudit(action = "AGENT_EVAL_STOP", label = "停止智能体评测",
            targetType = "AGENT_EVAL")
    public Result<AgentEvalJobView> stop(@PathVariable String id) {
        return Result.ok(evalClient.stop(id));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
