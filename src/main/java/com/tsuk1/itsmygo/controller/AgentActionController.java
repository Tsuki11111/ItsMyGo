package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AgentActionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 浏览器侧的确认接口。**只有登录用户能确认**，agent 只能 prepare。
 *
 * <p>确认接口只收 actionNo，业务参数全部来自服务端快照 ——
 * 用户看到什么就执行什么，客户端无法在确认时偷改数量 / 地址 / 金额。
 */
@RestController
@RequestMapping("/api/agent/actions")
public class AgentActionController {

    private final AgentActionService actionService;

    public AgentActionController(AgentActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping
    public Result<List<AgentActionView>> list(@AuthenticationPrincipal CurrentUser user,
                                              @RequestParam(required = false) String sessionId) {
        return Result.ok(actionService.list(user.id(), sessionId));
    }

    @RateLimit(scene = "agent-action", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/{actionNo}/confirm")
    public Result<AgentActionView> confirm(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable String actionNo) {
        return Result.ok(actionService.confirm(user.id(), actionNo));
    }

    @RateLimit(scene = "agent-action", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/{actionNo}/cancel")
    public Result<AgentActionView> cancel(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable String actionNo) {
        return Result.ok(actionService.cancel(user.id(), actionNo));
    }
}
