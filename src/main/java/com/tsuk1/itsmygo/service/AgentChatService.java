package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.agent.AgentConverseRequest;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryView;
import com.tsuk1.itsmygo.dto.agent.AgentSessionView;
import com.tsuk1.itsmygo.dto.agent.AgentStopRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AgentChatService {

    SseEmitter converse(CurrentUser user, AgentConverseRequest request);

    boolean stop(CurrentUser user, AgentStopRequest request);

    AgentHistoryView history(CurrentUser user, String sessionId, int limit);

    List<AgentSessionView> sessions(CurrentUser user, int limit);
}
