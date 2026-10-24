package com.tsuk1.itsmygo;

import agent.v1.Event;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.ChatMessage;
import com.tsuk1.itsmygo.entity.Conversation;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.ChatMessageMapper;
import com.tsuk1.itsmygo.mapper.ConversationMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 催退款：仅 REFUNDING 订单可催，确认后以买小购（AGENT）身份提醒商家。 */
class AgentUrgeRefundControllerTest extends ApiTestBase {

    @Autowired
    private AgentToolTicketService ticketService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @MockitoBean
    private AgentGateway agentGateway;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private Order insertOrder(long userId, String orderNo, String status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(1L);
        order.setTotalAmount(new BigDecimal("66.00"));
        order.setOriginalAmount(new BigDecimal("66.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponName("");
        order.setStatus(status);
        order.setReceiver("张三");
        order.setPhone("13800000000");
        order.setAddress("浙江省杭州市西湖区文三路 1 号");
        order.setTrackingNo("");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        if (!"PENDING_PAYMENT".equals(status)) {
            order.setPaidAt(now);
        }
        if ("SHIPPED".equals(status)) {
            order.setShippedAt(now);
        }
        if ("REFUNDING".equals(status) || "REFUNDED".equals(status)) {
            order.setStatusBeforeRefund("PAID");
            order.setRefundReason("商品有瑕疵");
            order.setRefundAppliedAt(now);
        }
        if ("REFUNDED".equals(status)) {
            order.setRefundHandledAt(now);
        }
        orderMapper.insert(order);
        return order;
    }

    private String prepare(long userId, String orderNo) throws Exception {
        String body = mockMvc.perform(post("/internal/agent/actions/urge-refund")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-refund"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("actionNo").asText();
    }

    private void expectPrepareCode(long userId, String orderNo, int code) throws Exception {
        mockMvc.perform(post("/internal/agent/actions/urge-refund")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-refund"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String confirm(String token, String actionNo) throws Exception {
        return mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
    }

    /** 模拟 agent：拿到 metadata 里的 ticket 后回调 Java 的发送工具。 */
    private void stubAgentSend(int expectedCode) {
        when(agentGateway.configured()).thenReturn(true);
        when(agentGateway.internalEvent(anyLong(), anyString(),
                eq("urge-refund-confirmed"), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String payload = invocation.getArgument(3);
                    String ticket = invocation.getArgument(4);
                    String no = objectMapper.readTree(payload).path("actionNo").asText();
                    mockMvc.perform(post("/internal/agent/actions/urge-refund/send")
                                    .header("X-Agent-Ticket", ticket)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"actionNo\":\"" + no + "\"}"))
                            .andExpect(jsonPath("$.code").value(expectedCode));
                    return List.<Event>of().iterator();
                });
    }

    @Test
    void prepareAndConfirmUrgeRefundSendsOneMessage() throws Exception {
        String username = "rg" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "RG" + System.nanoTime();
        insertOrder(uid, orderNo, "REFUNDING");

        String actionNo = prepare(uid, orderNo);
        assertThat(prepare(uid, orderNo)).isEqualTo(actionNo);

        stubAgentSend(200);

        mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "sess-refund"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("URGE_REFUND"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data[0].summary.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data[0].summary.merchantName").value("平台自营"))
                .andExpect(jsonPath("$.data[0].summary.statusLabel").value("退款中"))
                .andExpect(jsonPath("$.data[0].summary.refundReason").value("商品有瑕疵"));

        String confirmed = confirm(token, actionNo);
        assertThat(objectMapper.readTree(confirmed).path("data").path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(objectMapper.readTree(confirmed).path("data").path("result")
                .path("messageId").asLong()).isPositive();

        verify(agentGateway).internalEvent(eq(uid), eq("sess-refund"),
                eq("urge-refund-confirmed"), contains(actionNo), anyString(), any());

        confirm(token, actionNo);
        List<ChatMessage> messages = chatMessageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSenderUserId, uid)
                        .like(ChatMessage::getContent, orderNo));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSenderType()).isEqualTo(ChatMessage.SENDER_AGENT);
        assertThat(messages.get(0).getContentType()).isEqualTo(ChatMessage.TYPE_TEXT);
        assertThat(messages.get(0).getContent()).contains("催退款");

        Conversation conversation = conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getCustomerUserId, uid)
                        .eq(Conversation::getMerchantId, 1L));
        assertThat(conversation).isNotNull();
        assertThat(conversation.getMerchantUnread()).isEqualTo(1);

        expectPrepareCode(uid, orderNo, 400);
    }

    @Test
    void ineligibleAndForeignOrdersCannotBeUrged() throws Exception {
        String username = "ri" + System.nanoTime();
        customerToken(username);
        long uid = userId(username);

        expectPrepareCode(uid, insertOrder(uid, "RIP" + System.nanoTime(),
                "PENDING_PAYMENT").getOrderNo(), 400);
        expectPrepareCode(uid, insertOrder(uid, "RIA" + System.nanoTime(),
                "PAID").getOrderNo(), 400);
        expectPrepareCode(uid, insertOrder(uid, "RIS" + System.nanoTime(),
                "SHIPPED").getOrderNo(), 400);
        expectPrepareCode(uid, insertOrder(uid, "RIC" + System.nanoTime(),
                "COMPLETED").getOrderNo(), 400);
        expectPrepareCode(uid, insertOrder(uid, "RIR" + System.nanoTime(),
                "REFUNDED").getOrderNo(), 400);

        String otherName = "ro" + System.nanoTime();
        customerToken(otherName);
        long otherId = userId(otherName);
        String otherOrder = "RIO" + System.nanoTime();
        insertOrder(otherId, otherOrder, "REFUNDING");
        expectPrepareCode(uid, otherOrder, 404);
    }

    @Test
    void confirmRevalidatesStatusAfterMerchantRefunds() throws Exception {
        String username = "rr" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "RR" + System.nanoTime();
        insertOrder(uid, orderNo, "REFUNDING");
        String actionNo = prepare(uid, orderNo);

        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        order.setStatus("REFUNDED");
        order.setRefundHandledAt(LocalDateTime.now().withNano(0));
        orderMapper.updateById(order);

        stubAgentSend(400);
        mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage")
                        .value(containsString("商家已处理退款")));

        assertThat(chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSenderUserId, uid)
                .like(ChatMessage::getContent, orderNo))).isEmpty();
    }
}
