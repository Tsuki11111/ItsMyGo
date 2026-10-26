package com.tsuk1.itsmygo.dto.eval;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** 评测任务快照（与 rag_helper /eval/jobs 对齐）。 */
public record AgentEvalJobView(
        String id,
        String status,
        double createdAt,
        Double startedAt,
        Double finishedAt,
        Long elapsedMs,
        Params params,
        int total,
        int done,
        String currentCase,
        double percent,
        Result result,
        String error,
        List<String> warnings) {

    public record Params(
            List<String> caseIds,
            int limit,
            int runs,
            boolean web,
            boolean keep,
            int caseCount,
            String judgeModel) {
    }

    public record Result(String jsonl, String report, JsonNode summary) {
    }
}
