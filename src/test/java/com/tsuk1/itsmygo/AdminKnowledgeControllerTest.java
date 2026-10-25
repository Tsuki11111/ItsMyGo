package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.agent.KnowledgeImportClient;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeDocumentList;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeDocumentView;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeUploadResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 知识库管理：只有管理员能访问；上传代理转发给导入服务，扩展名在 Java 侧先挡。 */
class AdminKnowledgeControllerTest extends ApiTestBase {

    @MockitoBean
    private KnowledgeImportClient importClient;

    @Test
    void adminCanListAndUploadKnowledgeDocuments() throws Exception {
        when(importClient.documents()).thenReturn(new KnowledgeDocumentList(1,
                List.of(new KnowledgeDocumentView(
                        "manual", "测试产品", 12, "2026-10-25 10:00:00", "completed", 3))));
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(get("/api/admin/knowledge/documents")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.documents[0].fileTitle").value("manual"));

        when(importClient.upload(anyList(), eq(true))).thenReturn(
                new KnowledgeUploadResult("受理 1 个文件", List.of("task-1"), List.of(), List.of()));
        MockMultipartFile file = new MockMultipartFile(
                "files", "manual.pdf", "application/pdf", "pdf".getBytes());
        mockMvc.perform(multipart("/api/admin/knowledge/upload")
                        .file(file)
                        .param("force", "true")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskIds[0]").value("task-1"));
        verify(importClient).upload(anyList(), eq(true));
    }

    @Test
    void customerCannotUseKnowledgeAdmin() throws Exception {
        String customer = customerToken("cust" + System.nanoTime());
        mockMvc.perform(get("/api/admin/knowledge/documents")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRejectsUnsupportedExtensionBeforeCallingImportService() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MockMultipartFile file = new MockMultipartFile(
                "files", "evil.txt", "text/plain", "not a document".getBytes());
        mockMvc.perform(multipart("/api/admin/knowledge/upload")
                        .file(file)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(importClient);
    }
}
