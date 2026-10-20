package com.tsuk1.itsmygo.dto.agent;

import java.util.List;

public record AgentHistoryMessage(
        String role,
        String text,
        List<AgentImage> images,
        String ts,
        List<AgentTrace> traces) {

    public record AgentImage(String url, String caption) {
    }

    /** 该轮执行流；老消息没有 trace，返回空列表。 */
    public record AgentTrace(String kind, String agent, String detail) {
    }
}
