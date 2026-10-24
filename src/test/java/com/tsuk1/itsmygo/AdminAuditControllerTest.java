package com.tsuk1.itsmygo;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminAuditControllerTest extends ApiTestBase {

    @Test
    void successfulAndFailedAdminWritesAreAudited() throws Exception {
        String admin = adminToken("aa" + System.nanoTime());
        String trace = "audit-" + System.nanoTime();

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .header("X-Trace-Id", trace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"审计分类"
                                + System.nanoTime() + "\",\"sort\":0,\"enabled\":true}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearer(admin))
                        .param("action", "CATEGORY_CREATE")
                        .param("result", "SUCCESS")
                        .param("keyword", trace))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("CATEGORY_CREATE"))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].traceId").value(trace))
                .andExpect(jsonPath("$.data.records[0].actorUsername").isNotEmpty());

        String failedTrace = "audit-failed-" + System.nanoTime();
        mockMvc.perform(post("/api/admin/merchants/99999999/approve")
                        .header("Authorization", bearer(admin))
                        .header("X-Trace-Id", failedTrace))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearer(admin))
                        .param("result", "FAILED")
                        .param("keyword", failedTrace))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("MERCHANT_APPROVE"))
                .andExpect(jsonPath("$.data.records[0].result").value("FAILED"))
                .andExpect(jsonPath("$.data.records[0].errorMessage").isNotEmpty());
    }

    @Test
    void readRequestsAndAuditEndpointAreNotWrittenToAudit() throws Exception {
        String admin = adminToken("aa" + System.nanoTime());
        String trace = "audit-read-" + System.nanoTime();

        mockMvc.perform(get("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .header("X-Trace-Id", trace))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearer(admin))
                        .header("X-Trace-Id", trace)
                        .param("keyword", trace))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
