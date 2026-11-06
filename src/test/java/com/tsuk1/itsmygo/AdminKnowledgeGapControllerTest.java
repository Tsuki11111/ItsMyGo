package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.agent.KnowledgeGapClient;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapListView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapReportView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapView;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 知识缺口管理端：只有管理员可用；草稿/发布都要走代理，且参数非法时不碰客户端。 */
class AdminKnowledgeGapControllerTest extends ApiTestBase {

    @MockitoBean
    private KnowledgeGapClient gapClient;

    @Test
    void adminCanListReportDraftAndStatus() throws Exception {
        when(gapClient.list(anyDouble(), anyInt())).thenReturn(new KnowledgeGapListView(
                7, 1, List.of(new KnowledgeGapView(
                        "gap_abc123", "HAK 180 可以连接 WiFi 吗？", 3,
                        "2026-11-01 10:00:00", "2026-11-02 11:00:00",
                        List.of("Brother HAK 180 烫金机"), 0, 0, "pending", ""))));
        when(gapClient.report(anyDouble())).thenReturn(
                new KnowledgeGapReportView(7, "# 知识缺口周报\n\n- 缺口问题：1 个"));
        when(gapClient.draft(any(KnowledgeGapDraftRequest.class))).thenReturn(
                new KnowledgeGapDraftView("产品使用 FAQ（缺口补齐草稿）",
                        "# 产品使用 FAQ\n\n> 待人工核实：示例",
                        List.of("gap_abc123"), "草稿未入库"));
        when(gapClient.updateStatus(any(KnowledgeGapStatusRequest.class))).thenReturn(
                new KnowledgeGapStatusView(1, "resolved"));

        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(get("/api/admin/knowledge-gaps")
                        .param("days", "7").param("minCount", "1")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.gaps[0].gapId").value("gap_abc123"))
                .andExpect(jsonPath("$.data.gaps[0].products[0]")
                        .value("Brother HAK 180 烫金机"));

        mockMvc.perform(get("/api/admin/knowledge-gaps/report")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.markdown").value(
                        org.hamcrest.Matchers.containsString("知识缺口周报")));

        mockMvc.perform(post("/api/admin/knowledge-gaps/draft")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gapIds":["gap_abc123"],"days":7,"title":"草稿"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.markdown").value(
                        org.hamcrest.Matchers.containsString("待人工核实")));
        verify(gapClient).draft(any(KnowledgeGapDraftRequest.class));

        mockMvc.perform(post("/api/admin/knowledge-gaps/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gapIds":["gap_abc123"],"status":"resolved","note":"已补"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.status").value("resolved"));
    }

    @Test
    void publishReturnsTaskIdAndRejectsEmptyMarkdown() throws Exception {
        when(gapClient.publish(any(KnowledgeGapPublishRequest.class))).thenReturn(
                new KnowledgeGapPublishView("task-1", "产品使用 FAQ", true, "已进入导入流程"));
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(post("/api/admin/knowledge-gaps/publish")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"产品使用 FAQ","markdown":"# 产品使用 FAQ\\n\\n内容",
                                 "gapIds":["gap_abc123"]}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-1"))
                .andExpect(jsonPath("$.data.statusUpdated").value(true));
        verify(gapClient).publish(any(KnowledgeGapPublishRequest.class));

        mockMvc.perform(post("/api/admin/knowledge-gaps/publish")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"产品使用 FAQ","markdown":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(gapClient);
    }

    @Test
    void invalidDraftRequestDoesNotCallAgent() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        mockMvc.perform(post("/api/admin/knowledge-gaps/draft")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gapIds":[]}
                                """))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(gapClient);

        mockMvc.perform(post("/api/admin/knowledge-gaps/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gapIds":["gap_abc123"],"status":"whatever"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void customerCannotUseKnowledgeGapAdmin() throws Exception {
        String customer = customerToken("cust" + System.nanoTime());
        mockMvc.perform(get("/api/admin/knowledge-gaps")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
