package com.tsuk1.itsmygo.dto.eval;

import java.util.List;

/** 最近评测任务列表。 */
public record AgentEvalJobListView(List<AgentEvalJobView> items) {
}
