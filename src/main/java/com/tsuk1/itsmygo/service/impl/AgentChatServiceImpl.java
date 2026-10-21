package com.tsuk1.itsmygo.service.impl;

import agent.v1.Event;
import agent.v1.HistoryMessage;
import agent.v1.SessionInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentEventMapper;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentProperties;
import com.tsuk1.itsmygo.common.agent.AgentSseSink;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.agent.AgentConverseRequest;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryMessage;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryView;
import com.tsuk1.itsmygo.dto.agent.AgentSessionView;
import com.tsuk1.itsmygo.dto.agent.AgentStopRequest;
import com.tsuk1.itsmygo.entity.AgentSession;
import com.tsuk1.itsmygo.mapper.AgentSessionMapper;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AgentChatService;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private static final int MAX_HISTORY = 50;

    private final AgentGateway gateway;
    private final AgentProperties properties;
    private final AgentSessionMapper sessionMapper;
    private final AgentToolTicketService toolTicketService;
    private final ExecutorService agentExecutor;
    private final ScheduledExecutorService auxExecutor;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> activeStreams = new ConcurrentHashMap<>();

    public AgentChatServiceImpl(AgentGateway gateway, AgentProperties properties,
                                AgentSessionMapper sessionMapper,
                                AgentToolTicketService toolTicketService,
                                @Qualifier("agentExecutor") ExecutorService agentExecutor,
                                @Qualifier("agentAuxExecutor") ScheduledExecutorService auxExecutor,
                                ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.toolTicketService = toolTicketService;
        this.agentExecutor = agentExecutor;
        this.auxExecutor = auxExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter converse(CurrentUser user, AgentConverseRequest request) {
        requireConfigured();
        String message = request.message().trim();
        String sessionId;
        if (StringUtils.hasText(request.sessionId())) {
            sessionId = request.sessionId().trim();
            requireOwned(sessionId, user.id());
            sessionMapper.touch(sessionId, user.id());
        } else {
            // Java 先生成并记录归属，再调 Converse；不能等 SessionStart 回来才写，
            // 否则异步线程写库有竞态，客户端也可能拿到未登记的 id。
            sessionId = UUID.randomUUID().toString().replace("-", "");
            sessionMapper.insertSession(user.id(), sessionId);
        }

        if (activeStreams.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "该会话正在回复中，请稍候");
        }

        // 本轮工具票据：agent 的 order-query 工具回调 /internal/agent/** 时带回。
        // 只放 metadata，不下发浏览器，也不进模型上下文。
        String toolTicket = toolTicketService.issue(user.id(), sessionId);

        SseEmitter emitter = new SseEmitter(properties.deadlineSeconds() * 1000L + 30_000);
        AgentSseSink sink = new AgentSseSink(emitter, objectMapper, auxExecutor);
        Context.CancellableContext context = Context.current().withCancellation();
        AtomicReference<String> runIdRef = new AtomicReference<>();
        AtomicBoolean finalSeen = new AtomicBoolean(false);
        AtomicBoolean errorSeen = new AtomicBoolean(false);
        AtomicBoolean released = new AtomicBoolean(false);

        String fixedSessionId = sessionId;
        Runnable cleanup = () -> {
            context.cancel(null);
            sink.close();
            if (released.compareAndSet(false, true)) {
                activeStreams.remove(fixedSessionId);
            }
            String runId = runIdRef.get();
            if (runId != null && !finalSeen.get() && !errorSeen.get()) {
                // gRPC context cancel 只断流；Python worker 阻塞在队列上，必须用 runId 停
                auxExecutor.execute(() -> {
                    try {
                        gateway.stop(user.id(), fixedSessionId, runId);
                    } catch (Exception e) {
                        log.debug("客服 Stop 失败（忽略）: {}", e.getMessage());
                    }
                });
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        sink.start();
        try {
            agentExecutor.execute(() -> stream(user, fixedSessionId, message, toolTicket, sink, context,
                    runIdRef, finalSeen, errorSeen, cleanup));
        } catch (RejectedExecutionException e) {
            cleanup.run();
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "客服繁忙，请稍后再试");
        }
        return emitter;
    }

    private void stream(CurrentUser user, String sessionId, String message, String toolTicket,
                        AgentSseSink sink, Context.CancellableContext context,
                        AtomicReference<String> runIdRef,
                        AtomicBoolean finalSeen, AtomicBoolean errorSeen, Runnable cleanup) {
        try {
            Iterator<Event> events = context.call(() -> gateway.converse(
                    user.id(), sessionId, message,
                    Duration.ofSeconds(properties.deadlineSeconds()), toolTicket));

            while (!sink.isClosed() && events.hasNext()) {
                Event event = events.next();
                Event.KindCase kind = event.getKindCase();
                if (kind == Event.KindCase.START) {
                    runIdRef.set(event.getStart().getRunId());
                } else if (kind == Event.KindCase.FINAL) {
                    finalSeen.set(true);
                } else if (kind == Event.KindCase.ERROR) {
                    errorSeen.set(true);
                    log.warn("买小购返回错误事件: {}", event.getError().getMessage());
                }
                AgentEventMapper.Mapped mapped = AgentEventMapper.map(event);
                if (mapped != null) {
                    sink.send(mapped.event(), mapped.data());
                }
            }

            if (!sink.isClosed()) {
                String reason = finalSeen.get() ? "final" : errorSeen.get() ? "error" : "stopped";
                sink.send("done", AgentEventMapper.done(reason));
            }
        } catch (StatusRuntimeException e) {
            if (!sink.isClosed()) {
                log.warn("买小购 gRPC 流失败: {} {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                sink.send("error", grpcError(e));
                sink.send("done", AgentEventMapper.done("error"));
            }
        } catch (Exception e) {
            if (!sink.isClosed()) {
                log.error("买小购流处理异常", e);
                sink.send("error", AgentEventMapper.error(
                        "AGENT_ERROR", "客服处理失败，请稍后重试", true));
                sink.send("done", AgentEventMapper.done("error"));
            }
        } finally {
            cleanup.run();
        }
    }

    @Override
    public boolean stop(CurrentUser user, AgentStopRequest request) {
        requireConfigured();
        requireOwned(request.sessionId(), user.id());
        try {
            boolean stopped = gateway.stop(user.id(), request.sessionId(), request.runId());
            sessionMapper.touch(request.sessionId(), user.id());
            return stopped;
        } catch (StatusRuntimeException e) {
            log.warn("停止买小购失败: {} {}", e.getStatus().getCode(), e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "停止失败，请稍后再试");
        }
    }

    @Override
    public AgentHistoryView history(CurrentUser user, String sessionId, int limit) {
        requireConfigured();
        requireOwned(sessionId, user.id());
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        try {
            List<HistoryMessage> raw = gateway.history(user.id(), sessionId, safeLimit);
            List<AgentHistoryMessage> messages = raw.stream()
                    .map(item -> new AgentHistoryMessage(
                            item.getRole(),
                            item.getText(),
                            item.getImagesList().stream()
                                    .map(image -> new AgentHistoryMessage.AgentImage(
                                            image.getUrl(), image.getCaption()))
                                    .toList(),
                            item.getTs(),
                            item.getTraceList().stream()
                                    .map(trace -> new AgentHistoryMessage.AgentTrace(
                                            trace.getKind(), trace.getAgent(), trace.getDetail()))
                                    .toList()))
                    .toList();
            sessionMapper.touch(sessionId, user.id());
            return new AgentHistoryView(messages);
        } catch (StatusRuntimeException e) {
            log.warn("加载买小购历史失败: {} {}", e.getStatus().getCode(),
                    e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服历史加载失败，请稍后再试");
        }
    }

    @Override
    public List<AgentSessionView> sessions(CurrentUser user, int limit) {
        requireConfigured();
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        try {
            // agent 已按 x-user-id 过滤；这里再按 Java 的归属表过滤一次，双保险
            Set<String> owned = sessionMapper.selectList(
                            Wrappers.<AgentSession>lambdaQuery()
                                    .eq(AgentSession::getUserId, user.id()))
                    .stream().map(AgentSession::getSessionId).collect(Collectors.toSet());
            return gateway.sessions(user.id(), safeLimit).stream()
                    .filter(item -> owned.contains(item.getSessionId()))
                    .map(this::toSessionView)
                    .toList();
        } catch (StatusRuntimeException e) {
            log.warn("加载买小购会话列表失败: {} {}", e.getStatus().getCode(),
                    e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服会话列表加载失败，请稍后再试");
        }
    }

    private AgentSessionView toSessionView(SessionInfo info) {
        return new AgentSessionView(info.getSessionId(), info.getTitle(),
                info.getCount(), info.getLastAt());
    }

    private void requireConfigured() {
        if (!gateway.configured()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服智能体未配置，请联系管理员");
        }
    }

    private void requireOwned(String sessionId, Long userId) {
        if (!StringUtils.hasText(sessionId)
                || sessionMapper.selectOwned(sessionId, userId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
    }

    private Map<String, Object> grpcError(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case UNAVAILABLE -> AgentEventMapper.error(
                    "AGENT_UNAVAILABLE", "客服智能体暂时不可用，请稍后再试", true);
            case DEADLINE_EXCEEDED -> AgentEventMapper.error(
                    "AGENT_TIMEOUT", "客服响应超时，请重试", true);
            case UNAUTHENTICATED, PERMISSION_DENIED -> AgentEventMapper.error(
                    "AGENT_AUTH_FAILED", "客服服务鉴权失败，请联系管理员", false);
            case RESOURCE_EXHAUSTED -> AgentEventMapper.error(
                    "AGENT_BUSY", "客服繁忙，请稍后再试", true);
            default -> AgentEventMapper.error(
                    "AGENT_ERROR", "客服服务异常，请稍后再试", true);
        };
    }
}
