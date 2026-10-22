package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import agent.v1.Event;
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

/** 催发货：prepare 只落草稿，确认后以买小购（AGENT）身份给商家发一条站内消息。 */
class AgentUrgeShipmentControllerTest extends ApiTestBase {

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
        orderMapper.insert(order);
        return order;
    }

    private String prepare(long userId, String orderNo) throws Exception {
        String body = mockMvc.perform(post("/internal/agent/actions/urge-shipment")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-urge"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("actionNo").asText();
    }

    private void expectPrepareCode(long userId, String orderNo, int code) throws Exception {
        mockMvc.perform(post("/internal/agent/actions/urge-shipment")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-urge"))
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

    /**
     * 模拟 agent 内部事件：从 metadata 里拿到 ticket，再回调 Java 的发送工具。
     * expectedCode=200 走成功路径；400 用于验证确认前已发货的重新校验。
     */
    private void stubAgentSend(int expectedCode) {
        when(agentGateway.configured()).thenReturn(true);
        when(agentGateway.internalEvent(anyLong(), anyString(),
                eq("urge-shipment-confirmed"), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String payload = invocation.getArgument(3);
                    String ticket = invocation.getArgument(4);
                    String no = objectMapper.readTree(payload).path("actionNo").asText();
                    mockMvc.perform(post("/internal/agent/actions/urge-shipment/send")
                                    .header("X-Agent-Ticket", ticket)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"actionNo\":\"" + no + "\"}"))
                            .andExpect(jsonPath("$.code").value(expectedCode));
                    return List.<Event>of().iterator();
                });
    }

    @Test
    void prepareAndConfirmUrgeSendsOneMessage() throws Exception {
        String username = "ug" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "UG" + System.nanoTime();
        insertOrder(uid, orderNo, "PAID");

        String actionNo = prepare(uid, orderNo);
        // 同一订单还有未过期草稿时，重复 prepare 返回同一张卡
        assertThat(prepare(uid, orderNo)).isEqualTo(actionNo);

        // agent 内部事件由 mock 触发：模拟 agent 调 Java 发送工具
        stubAgentSend(200);

        mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "sess-urge"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("URGE_SHIPMENT"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data[0].summary.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data[0].summary.merchantName").value("平台自营"))
                .andExpect(jsonPath("$.data[0].summary.statusLabel").value("待发货"));

        String confirmed = confirm(token, actionNo);
        assertThat(objectMapper.readTree(confirmed).path("data").path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(objectMapper.readTree(confirmed).path("data").path("result")
                .path("messageId").asLong()).isPositive();

        // 动作成功后 Java 触发 agent 内部事件，agent 再回调发送工具
        verify(agentGateway).internalEvent(eq(uid), eq("sess-urge"),
                eq("urge-shipment-confirmed"), contains(actionNo), anyString(), any());

        // 双击确认幂等：不会重复发消息
        confirm(token, actionNo);
        List<ChatMessage> messages = chatMessageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSenderUserId, uid)
                        .like(ChatMessage::getContent, orderNo));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSenderType()).isEqualTo(ChatMessage.SENDER_AGENT);
        assertThat(messages.get(0).getContentType()).isEqualTo(ChatMessage.TYPE_TEXT);
        assertThat(messages.get(0).getContent()).contains("催发货");

        Conversation conversation = conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getCustomerUserId, uid)
                        .eq(Conversation::getMerchantId, 1L));
        assertThat(conversation).isNotNull();
        assertThat(conversation.getMerchantUnread()).isEqualTo(1);

        // 10 分钟内不允许再次催同一单
        expectPrepareCode(uid, orderNo, 400);
    }

    @Test
    void ineligibleAndForeignOrdersCannotBeUrged() throws Exception {
        String username = "ui" + System.nanoTime();
        customerToken(username);
        long uid = userId(username);

        String pending = "UP" + System.nanoTime();
        insertOrder(uid, pending, "PENDING_PAYMENT");
        expectPrepareCode(uid, pending, 400);

        String shipped = "US" + System.nanoTime();
        insertOrder(uid, shipped, "SHIPPED");
        expectPrepareCode(uid, shipped, 400);

        String completed = "UC" + System.nanoTime();
        insertOrder(uid, completed, "COMPLETED");
        expectPrepareCode(uid, completed, 400);

        String otherName = "uo" + System.nanoTime();
        customerToken(otherName);
        long otherId = userId(otherName);
        String otherOrder = "UO" + System.nanoTime();
        insertOrder(otherId, otherOrder, "PAID");
        expectPrepareCode(uid, otherOrder, 404);
    }

    @Test
    void confirmRevalidatesStatusAfterMerchantShips() throws Exception {
        String username = "ur" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "UR" + System.nanoTime();
        insertOrder(uid, orderNo, "PAID");
        String actionNo = prepare(uid, orderNo);

        // 确认前商家已发货：agent 调发送工具时 Java 必须重新校验，不能把催单发出去
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        order.setStatus("SHIPPED");
        order.setShippedAt(LocalDateTime.now().withNano(0));
        orderMapper.updateById(order);

        stubAgentSend(400);
        mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage")
                        .value(containsString("不可催发货")));

        assertThat(chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSenderUserId, uid)
                .like(ChatMessage::getContent, orderNo))).isEmpty();

        mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "sess-urge"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].errorMessage")
                        .value(containsString("不可催发货")));
    }
}
