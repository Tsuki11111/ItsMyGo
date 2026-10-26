package com.tsuk1.itsmygo.dto.eval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 启动一次智能体评测；cases 为空时按 limit 取前 N 条。 */
public record AgentEvalRunRequest(
        @Size(max = 100, message = "一次最多选择 100 条用例")
        List<String> cases,
        @Min(value = 0, message = "条数上限不能为负数")
        @Max(value = 100, message = "条数上限最多 100")
        Integer limit,
        @Min(value = 1, message = "重复次数至少 1")
        @Max(value = 10, message = "重复次数最多 10")
        Integer runs,
        Boolean web,
        Boolean keep) {

    public AgentEvalRunRequest {
        cases = cases == null ? List.of() : List.copyOf(cases);
        limit = limit == null ? 0 : limit;
        runs = runs == null ? 1 : runs;
        web = web != null && web;
        keep = keep != null && keep;
    }
}
