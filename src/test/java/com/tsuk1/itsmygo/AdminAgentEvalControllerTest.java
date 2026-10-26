package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.agent.AgentEvalClient;
import com.tsuk1.itsmygo.dto.eval.AgentEvalCasesView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalJobView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalResultListView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalRunRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理端智能体评测：只有管理员能访问，启动/停止代理到 rag_helper。 */
class AdminAgentEvalControllerTest extends ApiTestBase {

    @MockitoBean
    private AgentEvalClient evalClient;

    @Test
    void adminCanListCasesStartAndStopEvaluation() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        when(evalClient.cases()).thenReturn(new AgentEvalCasesView(
                List.of(new AgentEvalCasesView.AgentEvalCaseView(
                        "hak180_temperature", "烫金温度怎么设置？", "fact",
                        "hak180使用说明", "real", "answered", true)),
                "deepseek-v4-pro", List.of(), List.of()));
        when(evalClient.results(50)).thenReturn(List.of(
                new AgentEvalResultListView.AgentEvalResultFileView(
                        "judge_20261026-054613.jsonl", "judge_20261026-054613.md",
                        1, 11836, 1792993698.0)));

        mockMvc.perform(get("/api/admin/agent-eval/cases")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.judgeModel").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.data.items[0].id").value("hak180_temperature"));

        mockMvc.perform(get("/api/admin/agent-eval/results")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].name")
                        .value("judge_20261026-054613.jsonl"));

        AgentEvalJobView running = new AgentEvalJobView(
                "job-1", "running", 1792993698.0, 1792993698.0, null, null,
                new AgentEvalJobView.Params(List.of("hak180_temperature"), 0, 1,
                        false, false, 1, "deepseek-v4-pro"),
                1, 0, "hak180_temperature", 0, null, "", List.of());
        when(evalClient.start(any(AgentEvalRunRequest.class))).thenReturn(running);
        when(evalClient.stop("job-1")).thenReturn(running);

        mockMvc.perform(post("/api/admin/agent-eval/runs")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cases":["hak180_temperature"],"runs":1,"web":false,"keep":false}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("running"));

        mockMvc.perform(post("/api/admin/agent-eval/jobs/job-1/stop")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("job-1"));
        verify(evalClient).start(any(AgentEvalRunRequest.class));
        verify(evalClient).stop("job-1");
    }

    @Test
    void customerCannotUseAgentEvalAdmin() throws Exception {
        String customer = customerToken("cust" + System.nanoTime());
        mockMvc.perform(get("/api/admin/agent-eval/cases")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(evalClient);
    }
}
