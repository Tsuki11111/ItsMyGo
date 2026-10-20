package com.tsuk1.itsmygo.common.agent;

import agent.v1.Event;
import agent.v1.FinalEvent;
import agent.v1.Image;
import agent.v1.Trace;
import agent.v1.Usage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** proto Event → 前端 SSE 事件；SSE data 统一是 JSON 对象。 */
public final class AgentEventMapper {

    private AgentEventMapper() {
    }

    public record Mapped(String event, Map<String, Object> data) {
    }

    public static Mapped map(Event event) {
        return switch (event.getKindCase()) {
            case START -> new Mapped("start", start(event.getStart().getSessionId(),
                    event.getStart().getRunId()));
            case TRACE -> new Mapped("trace", trace(event.getTrace()));
            case DELTA -> new Mapped("delta", Map.of("delta", event.getDelta().getDelta()));
            case USAGE -> new Mapped("usage", usage(event.getUsage()));
            case FINAL -> new Mapped("final", finalEvent(event.getFinal()));
            // Python 侧的 ErrorEvent 只带 message；对用户一律用通用文案，原文由 Service 记日志
            case ERROR -> new Mapped("error",
                    error("AGENT_ERROR", "客服处理失败，请稍后重试", true));
            default -> null;
        };
    }

    public static Map<String, Object> start(String sessionId, String runId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("runId", runId);
        return data;
    }

    public static Map<String, Object> done(String reason) {
        return Map.of("reason", reason);
    }

    public static Map<String, Object> error(String code, String message, boolean retryable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        data.put("retryable", retryable);
        return data;
    }

    private static Map<String, Object> trace(Trace trace) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", trace.getKind());
        data.put("agent", trace.getAgent());
        data.put("detail", trace.getDetail());
        return data;
    }

    private static Map<String, Object> usage(Usage usage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("calls", usage.getCalls());
        data.put("failed", usage.getFailed());
        data.put("totalTokens", usage.getTotalTokens());
        data.put("cost", usage.getCost());
        data.put("elapsedMs", usage.getElapsedMs());
        data.put("unpricedCalls", usage.getUnpricedCalls());
        return data;
    }

    private static Map<String, Object> finalEvent(FinalEvent finalEvent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", finalEvent.getAnswer());
        data.put("webOnly", finalEvent.getWebOnly());
        List<Map<String, Object>> images = new ArrayList<>();
        for (Image image : finalEvent.getImagesList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", image.getUrl());
            item.put("caption", image.getCaption());
            images.add(item);
        }
        data.put("images", images);
        return data;
    }
}
