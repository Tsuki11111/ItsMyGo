package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeRefundRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeShipmentRequest;

import java.util.List;

/**
 * 客服智能体的写操作：先落「待确认动作」，用户确认后才真正调业务 Service。
 * agent 只能 prepare，不能 confirm —— 确认必须来自浏览器里的登录用户。
 */
public interface AgentActionService {

    AgentActionView prepareOrder(long userId, String sessionId, AgentOrderDraftRequest request);

    AgentActionView prepareRefund(long userId, String sessionId, AgentRefundDraftRequest request);

    AgentActionView prepareUrgeShipment(long userId, String sessionId,
                                        AgentUrgeShipmentRequest request);

    AgentActionView prepareUrgeRefund(long userId, String sessionId,
                                      AgentUrgeRefundRequest request);

    /** agent 确认后调用：校验归属 / 状态 / 幂等，以 AGENT 身份发送商家站内消息。 */
    AgentActionView executeUrgeShipment(long userId, String sessionId, String actionNo);

    AgentActionView executeUrgeRefund(long userId, String sessionId, String actionNo);

    AgentActionView confirm(long userId, String actionNo);

    AgentActionView cancel(long userId, String actionNo);

    List<AgentActionView> list(long userId, String sessionId);
}
