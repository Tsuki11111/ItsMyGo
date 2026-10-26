package com.tsuk1.itsmygo.dto.eval;

import java.util.List;

/** 可用于评测的用例与 judge 配置（与 rag_helper /eval/cases 对齐）。 */
public record AgentEvalCasesView(
        List<AgentEvalCaseView> items,
        String judgeModel,
        List<String> configErrors,
        List<String> warnings) {

    public record AgentEvalCaseView(
            String id,
            String question,
            String category,
            String doc,
            String source,
            String expectedOutcome,
            boolean hasGold) {
    }
}
