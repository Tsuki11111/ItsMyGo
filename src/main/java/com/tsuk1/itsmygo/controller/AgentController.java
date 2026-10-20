package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.agent.AgentConverseRequest;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryView;
import com.tsuk1.itsmygo.dto.agent.AgentSessionView;
import com.tsuk1.itsmygo.dto.agent.AgentStopRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AgentChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 买小购客服：浏览器用 fetch POST 建立 SSE 流（POST 可以带 Authorization），
 * 每次 Converse 的事件流只属于这次调用，不需要全局 SSE 连接和票据。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentChatService agentChatService;

    public AgentController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @RateLimit(scene = "agent-converse", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping(value = "/converse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter converse(@AuthenticationPrincipal CurrentUser user,
                               @Valid @RequestBody AgentConverseRequest request,
                               HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return agentChatService.converse(user, request);
    }

    @PostMapping("/stop")
    public Result<Boolean> stop(@AuthenticationPrincipal CurrentUser user,
                                @Valid @RequestBody AgentStopRequest request) {
        return Result.ok(agentChatService.stop(user, request));
    }

    @GetMapping("/history")
    public Result<AgentHistoryView> history(@AuthenticationPrincipal CurrentUser user,
                                            @RequestParam String sessionId,
                                            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(agentChatService.history(user, sessionId, limit));
    }

    /** 当前用户的历史会话（跨设备可用）；agent 与 Java 双侧都会按用户过滤。 */
    @GetMapping("/sessions")
    public Result<List<AgentSessionView>> sessions(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(agentChatService.sessions(user, limit));
    }
}
