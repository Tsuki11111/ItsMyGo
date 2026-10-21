package com.tsuk1.itsmygo.common.agent;

import agent.v1.Event;
import agent.v1.HistoryMessage;
import agent.v1.SessionInfo;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;

/**
 * 客服智能体 gRPC 网关。抽成接口便于 Controller/Service 测试时 mock，
 * 不依赖真实 8003 服务。
 */
public interface AgentGateway {

    boolean configured();

    /**
     * @param toolTicket 本轮工具调用票据（由 Java 签发，经 metadata 下发给 agent；
     *                   agent 回调 /internal/agent/** 时带回，见 AgentToolTicketService）
     */
    Iterator<Event> converse(long userId, String sessionId, String message, Duration deadline,
                             String toolTicket);

    boolean stop(long userId, String sessionId, String runId);

    List<HistoryMessage> history(long userId, String sessionId, int limit);

    List<SessionInfo> sessions(long userId, int limit);
}
