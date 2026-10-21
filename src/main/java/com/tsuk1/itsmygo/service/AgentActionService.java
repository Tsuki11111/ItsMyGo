package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;

import java.util.List;

/**
 * 客服智能体的写操作：先落「待确认动作」，用户确认后才真正调业务 Service。
 * agent 只能 prepare，不能 confirm —— 确认必须来自浏览器里的登录用户。
 */
public interface AgentActionService {

    AgentActionView prepareOrder(long userId, String sessionId, AgentOrderDraftRequest request);

    AgentActionView prepareRefund(long userId, String sessionId, AgentRefundDraftRequest request);

    AgentActionView confirm(long userId, String actionNo);

    AgentActionView cancel(long userId, String actionNo);

    List<AgentActionView> list(long userId, String sessionId);
}
