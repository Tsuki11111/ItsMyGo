package com.tsuk1.itsmygo.dto.eval;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 一份评测结果。
 *
 * rows / summary 保留 agent 的原始 JSON 结构：逐条结果里的 claims、evidence
 * 层级较深，且属于评测展示层数据，不在 Java 侧重复建模。
 */
public record AgentEvalResultView(
        String name,
        String kind,
        List<JsonNode> rows,
        JsonNode summary,
        String report,
        String reportContent) {
}
