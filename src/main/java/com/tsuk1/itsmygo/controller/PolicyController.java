package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicyDetailView;
import com.tsuk1.itsmygo.dto.policy.PlatformPolicySummaryView;
import com.tsuk1.itsmygo.service.PlatformPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台政策公开读取：政策本身对用户公开；写入口后续由管理端政策中心提供。
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PlatformPolicyService policyService;

    public PolicyController(PlatformPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public Result<List<PlatformPolicySummaryView>> list() {
        return Result.ok(policyService.list());
    }

    @GetMapping("/{policyKey}")
    public Result<PlatformPolicyDetailView> detail(@PathVariable String policyKey) {
        return Result.ok(policyService.get(policyKey));
    }
}
