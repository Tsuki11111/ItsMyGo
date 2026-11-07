package com.tsuk1.itsmygo;

import agent.v1.Event;
import agent.v1.FinalEvent;
import agent.v1.FormField;
import agent.v1.FormOption;
import agent.v1.FormRequest;
import agent.v1.FormResult;
import agent.v1.HistoryMessage;
import agent.v1.SessionInfo;
import agent.v1.SessionStart;
import agent.v1.Trace;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.AgentSession;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AgentSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest extends ApiTestBase {

    @MockitoBean
    private AgentGateway agentGateway;

    @Autowired
    private AgentSessionMapper agentSessionMapper;

    @Autowired
    private AgentToolTicketService toolTicketService;

    private final java.util.concurrent.atomic.AtomicReference<String> capturedTicket =
            new java.util.concurrent.atomic.AtomicReference<>();

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    @Test
    void converseStreamsSseAndRecordsSessionOwnership() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        java.util.concurrent.atomic.AtomicReference<FormResult> oldClientForm =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(agentGateway.converse(anyLong(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String sessionId = invocation.getArgument(1);
                    oldClientForm.set(invocation.getArgument(3));
                    return List.of(
                            Event.newBuilder().setStart(SessionStart.newBuilder()
                                    .setSessionId(sessionId).setRunId("run-1")).build(),
                            Event.newBuilder().setTrace(Trace.newBuilder()
                                    .setKind("delegate").setAgent("product-kb")
                                    .setDetail("检索产品手册")).build(),
                            Event.newBuilder().setFinal(FinalEvent.newBuilder()
                                    .setAnswer("answer").setWebOnly(false)).build()
                    ).iterator();
                });

        String username = "ag" + System.nanoTime();
        String token = customerToken(username);

        MvcResult result = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:trace")))
                .andExpect(content().string(containsString("event:final")))
                .andExpect(content().string(containsString("event:done")));

        List<AgentSession> sessions = agentSessionMapper.selectList(
                Wrappers.<AgentSession>lambdaQuery()
                        .eq(AgentSession::getUserId, userId(username)));
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getSessionId()).isNotBlank();
        // 老客户端不带 formResult：透传 null，不能报错
        assertThat(oldClientForm.get()).isNull();
    }

    @Test
    void conversePassesFormResultAndReturnsFormsInFinalEvent() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        java.util.concurrent.atomic.AtomicReference<FormResult> capturedForm =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(agentGateway.converse(anyLong(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    capturedForm.set(invocation.getArgument(3));
                    return List.of(Event.newBuilder()
                            .setFinal(FinalEvent.newBuilder()
                                    .setAnswer("ok")
                                    .addForms(FormRequest.newBuilder()
                                            .setFormId("f1")
                                            .setFormType("refund_reason")
                                            .setTitle("退款原因")
                                            .setDescription("请选择退款原因")
                                            .addFields(FormField.newBuilder()
                                                    .setName("reason")
                                                    .setLabel("退款原因")
                                                    .setType("select")
                                                    .setRequired(true)
                                                    .setDefaultValue("quality")
                                                    .addOptions(FormOption.newBuilder()
                                                            .setValue("quality")
                                                            .setLabel("商品质量问题")))
                                            .setSubmitLabel("提交")
                                            .setExpiresInSeconds(600)
                                            .putMeta("orderNo", "NO1")))
                            .build()).iterator();
                });

        String token = customerToken("fm" + System.nanoTime());
        String formId = "f1-" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"已提交","formResult":{"formId":"%s","values":{"reason":"商品质量问题"}}}
                                """.formatted(formId)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"forms\":[")))
                .andExpect(content().string(containsString("\"formId\":\"f1\"")))
                .andExpect(content().string(containsString("\"formType\":\"refund_reason\"")))
                .andExpect(content().string(containsString("\"defaultValue\":\"quality\"")))
                .andExpect(content().string(containsString("\"expiresInSeconds\":600")))
                .andExpect(content().string(containsString("\"orderNo\":\"NO1\"")));

        assertThat(capturedForm.get()).isNotNull();
        assertThat(capturedForm.get().getFormId()).isEqualTo(formId);
        assertThat(capturedForm.get().getValuesMap()).containsEntry("reason", "商品质量问题");
    }

    @Test
    void finalFillsRefundReasonOptionsFromAfterSaleDictionary() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        when(agentGateway.converse(anyLong(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> List.of(Event.newBuilder()
                        .setFinal(FinalEvent.newBuilder()
                                .setAnswer("请选择退款原因")
                                .addForms(FormRequest.newBuilder()
                                        .setFormId("f-" + System.nanoTime())
                                        .setFormType("refund_reason")
                                        .setTitle("选择退款原因")
                                        .addFields(FormField.newBuilder()
                                                .setName("reason")
                                                .setLabel("退款原因")
                                                .setType("select")
                                                .setRequired(true))
                                        .setExpiresInSeconds(600)))
                        .build()).iterator());

        String token = customerToken("fd" + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"我要退款"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        // AI 侧只下发空 options，Java 用售后原因字典补全，value 用中文展示名
        String sse = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(sse)
                .contains("\"value\":\"商品质量问题\",\"label\":\"商品质量问题\"")
                .contains("\"label\":\"不想要了\"");
    }

    @Test
    void duplicateFormSubmissionIsRejectedWithoutCallingAgent() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        when(agentGateway.converse(anyLong(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> List.of(Event.newBuilder()
                        .setFinal(FinalEvent.newBuilder().setAnswer("ok").setWebOnly(false)).build())
                        .iterator());

        String token = customerToken("fu" + System.nanoTime());
        String formId = "dup-" + System.nanoTime();
        String body = """
                {"message":"已提交","formResult":{"formId":"%s","values":{"reason":"商品质量问题"}}}
                """.formatted(formId);

        MvcResult first = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(first))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:final")));

        // 同一 form_id 再提交：Redis SETNX 失败，直接 SSE error，不再调 agent
        MvcResult second = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(second))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FORM_ALREADY_SUBMITTED")));

        verify(agentGateway, times(1))
                .converse(anyLong(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void invalidRefundReasonIsRejectedWithoutCallingAgent() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        String token = customerToken("fv" + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"已提交","formResult":{"formId":"bad-%s",
                                 "values":{"reason":"我说了算"}}}
                                """.formatted(System.nanoTime())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FORM_INVALID")));

        verify(agentGateway, never())
                .converse(anyLong(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void sessionIsOnlyVisibleToItsOwner() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        String aName = "aa" + System.nanoTime();
        String bName = "ab" + System.nanoTime();
        String aToken = customerToken(aName);
        String bToken = customerToken(bName);
        long aId = userId(aName);
        String sessionId = "sess" + System.nanoTime();
        agentSessionMapper.insertSession(aId, sessionId);

        mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(bToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","message":"越权"}
                                """.formatted(sessionId)))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/agent/history")
                        .header("Authorization", bearer(bToken))
                        .param("sessionId", sessionId))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/agent/stop")
                        .header("Authorization", bearer(bToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","runId":"run-1"}
                                """.formatted(sessionId)))
                .andExpect(jsonPath("$.code").value(404));

        when(agentGateway.history(eq(aId), eq(sessionId), anyInt()))
                .thenReturn(List.of(HistoryMessage.newBuilder()
                        .setRole("user").setText("hello").setTs("2026-10-20")
                        .addTrace(Trace.newBuilder().setKind("delegate")
                                .setAgent("product-kb").setDetail("检索"))
                        .addForms(FormRequest.newBuilder()
                                .setFormId("hf1")
                                .setFormType("refund_reason")
                                .setTitle("退款原因")
                                .addFields(FormField.newBuilder()
                                        .setName("reason")
                                        .setLabel("退款原因")
                                        .setType("select")
                                        .setRequired(true)
                                        .setDefaultValue("quality")
                                        .addOptions(FormOption.newBuilder()
                                                .setValue("quality")
                                                .setLabel("商品质量问题")))
                                .setSubmitLabel("提交")
                                .setExpiresInSeconds(600)
                                .putMeta("orderNo", "NO1"))
                        .build()));

        mockMvc.perform(get("/api/agent/history")
                        .header("Authorization", bearer(aToken))
                        .param("sessionId", sessionId))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messages[0].text").value("hello"))
                .andExpect(jsonPath("$.data.messages[0].traces[0].detail").value("检索"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].formId").value("hf1"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].formType")
                        .value("refund_reason"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].fields[0].name").value("reason"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].fields[0].required").value(true))
                .andExpect(jsonPath("$.data.messages[0].forms[0].fields[0].defaultValue")
                        .value("quality"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].fields[0].options[0].value")
                        .value("quality"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].fields[0].options[0].label")
                        .value("商品质量问题"))
                .andExpect(jsonPath("$.data.messages[0].forms[0].expiresInSeconds").value(600))
                .andExpect(jsonPath("$.data.messages[0].forms[0].meta.orderNo").value("NO1"));
    }

    @Test
    void sessionsOnlyReturnOwnedOnes() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        String aName = "asa" + System.nanoTime();
        String bName = "asb" + System.nanoTime();
        String aToken = customerToken(aName);
        String bToken = customerToken(bName);
        long aId = userId(aName);
        String owned = "owned" + System.nanoTime();
        String foreign = "foreign" + System.nanoTime();
        agentSessionMapper.insertSession(aId, owned);

        when(agentGateway.sessions(eq(aId), anyInt())).thenReturn(List.of(
                SessionInfo.newBuilder().setSessionId(owned).setTitle("我的会话")
                        .setCount(2).setLastAt("2026-10-20").build(),
                SessionInfo.newBuilder().setSessionId(foreign).setTitle("别人的会话")
                        .setCount(1).setLastAt("2026-10-20").build()));

        mockMvc.perform(get("/api/agent/sessions")
                        .header("Authorization", bearer(aToken)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sessionId").value(owned))
                .andExpect(jsonPath("$.data[0].title").value("我的会话"));

        mockMvc.perform(get("/api/agent/sessions")
                        .header("Authorization", bearer(bToken)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void stopPassesRunIdToGateway() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        String name = "as" + System.nanoTime();
        String token = customerToken(name);
        long userId = userId(name);
        String sessionId = "sess" + System.nanoTime();
        agentSessionMapper.insertSession(userId, sessionId);
        when(agentGateway.stop(userId, sessionId, "run-9")).thenReturn(true);

        mockMvc.perform(post("/api/agent/stop")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","runId":"run-9"}
                                """.formatted(sessionId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(agentGateway).stop(userId, sessionId, "run-9");
    }

    @Test
    void unconfiguredAgentReturnsClearError() throws Exception {
        when(agentGateway.configured()).thenReturn(false);
        String token = customerToken("au" + System.nanoTime());

        mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}
                                """))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("未配置")));

        mockMvc.perform(get("/api/agent/history")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "whatever"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("未配置")));

        mockMvc.perform(post("/api/agent/stop")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"whatever","runId":"r"}
                                """))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("未配置")));
    }

    @Test
    void converseIssuesToolTicketBoundToUser() throws Exception {
        when(agentGateway.configured()).thenReturn(true);
        capturedTicket.set(null);
        when(agentGateway.converse(anyLong(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    capturedTicket.set(invocation.getArgument(5));
                    return List.of(Event.newBuilder()
                            .setFinal(FinalEvent.newBuilder().setAnswer("ok").setWebOnly(false)).build())
                            .iterator();
                });

        String username = "tk" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);

        MvcResult result = mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"订单到哪了"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        assertThat(capturedTicket.get()).isNotBlank();
        AgentToolTicketService.Identity identity = toolTicketService.resolve(capturedTicket.get());
        assertThat(identity).isNotNull();
        assertThat(identity.userId()).isEqualTo(uid);
    }

    @Test
    void requestValidation() throws Exception {
        String token = customerToken("av" + System.nanoTime());

        mockMvc.perform(post("/api/agent/converse")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   "}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/agent/stop")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"","runId":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }
}
