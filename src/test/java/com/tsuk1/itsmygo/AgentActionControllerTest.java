package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 待确认动作：prepare → 用户确认 → Java 执行；越权 / 过期 / 双击都要挡住。 */
class AgentActionControllerTest extends ApiTestBase {

    @Autowired
    private AgentToolTicketService ticketService;

    @Autowired
    private AgentActionMapper actionMapper;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private String ticket(long userId) {
        return ticketService.issue(userId, "sess-test");
    }

    private long createAddress(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "张三", "13800000000", "浙江省", "杭州市", "西湖区",
                                "文三路 1 号", true))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String prepareOrder(String ticket, long addressId, long productId, long skuId,
                                int quantity) throws Exception {
        String body = mockMvc.perform(post("/internal/agent/actions/order")
                        .header("X-Agent-Ticket", ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":%d,"items":[{"productId":%d,"skuId":%d,"quantity":%d}]}
                                """.formatted(addressId, productId, skuId, quantity)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("actionNo").asText();
    }

    private String confirm(String token, String actionNo) throws Exception {
        return mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void prepareAndConfirmOrderCreatesOrderOnce() throws Exception {
        String username = "aa" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "动作测试商品" + System.nanoTime(), "66.00", 10);
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(token);

        String actionNo = prepareOrder(ticket(uid), addressId, productId, skuId, 2);
        String prepareBody = mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "sess-test"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].actionNo").value(actionNo))
                .andExpect(jsonPath("$.data[0].summary.totalAmount").value("132.00"))
                .andExpect(jsonPath("$.data[0].summary.address.receiver").value("张三"))
                .andReturn().getResponse().getContentAsString();
        assertThat(prepareBody).contains("动作测试商品");

        String confirmed = confirm(token, actionNo);
        long orderId = objectMapper.readTree(confirmed).path("data").path("result")
                .path("orders").get(0).path("id").asLong();
        assertThat(objectMapper.readTree(confirmed).path("data").path("result")
                .path("orders").get(0).path("status").asText()).isEqualTo("PENDING_PAYMENT");

        // 再确认一次：幂等，不重复下单
        confirm(token, actionNo);
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer(token))
                        .param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].order.id").value(orderId));
        assertThat(productSkuMapper.selectById(skuId).getStock()).isEqualTo(8);

        // 支付后动作卡片要回查订单当前状态，不能停在执行时的「待支付」快照
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(token))
                        .param("sessionId", "sess-test"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].result.orders[0].status").value("PAID"))
                .andExpect(jsonPath("$.data[0].result.orders[0].statusLabel").value("待发货"));
    }

    @Test
    void anotherUserCannotConfirmOrSeeAction() throws Exception {
        String aName = "ab" + System.nanoTime();
        String bName = "ac" + System.nanoTime();
        String aToken = customerToken(aName);
        String bToken = customerToken(bName);
        long aId = userId(aName);
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "动作测试商品" + System.nanoTime(), "10.00", 5);
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(aToken);
        String actionNo = prepareOrder(ticket(aId), addressId, productId, skuId, 1);

        mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(bToken)))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/agent/actions")
                        .header("Authorization", bearer(bToken))
                        .param("sessionId", "sess-test"))
                .andExpect(jsonPath("$.data.length()").value(0));

        AgentAction action = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getActionNo, actionNo));
        assertThat(action.getStatus()).isEqualTo("PENDING_CONFIRM");
    }

    @Test
    void prepareRejectsForeignAddress() throws Exception {
        String aName = "ad" + System.nanoTime();
        String bName = "ae" + System.nanoTime();
        String aToken = customerToken(aName);
        String bToken = customerToken(bName);
        long aId = userId(aName);
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "动作测试商品" + System.nanoTime(), "10.00", 5);
        long skuId = defaultSkuId(productId);
        long bAddressId = createAddress(bToken);

        mockMvc.perform(post("/internal/agent/actions/order")
                        .header("X-Agent-Ticket", ticket(aId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":%d,"items":[{"productId":%d,"skuId":%d,"quantity":1}]}
                                """.formatted(bAddressId, productId, skuId)))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void expiredActionCannotExecute() throws Exception {
        String username = "af" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "动作测试商品" + System.nanoTime(), "10.00", 5);
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(token);
        String actionNo = prepareOrder(ticket(uid), addressId, productId, skuId, 1);

        AgentAction action = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getActionNo, actionNo));
        action.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        actionMapper.updateById(action);

        mockMvc.perform(post("/api/agent/actions/" + actionNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void prepareAndConfirmRefund() throws Exception {
        String username = "ag" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "动作测试商品" + System.nanoTime(), "20.00", 5);
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(token);
        String orderAction = prepareOrder(ticket(uid), addressId, productId, skuId, 1);
        String confirmed = confirm(token, orderAction);
        long orderId = objectMapper.readTree(confirmed).path("data").path("result")
                .path("orders").get(0).path("id").asLong();
        String orderNo = objectMapper.readTree(confirmed).path("data").path("result")
                .path("orders").get(0).path("orderNo").asText();

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.order.status").value("PAID"));

        String refundAction = mockMvc.perform(post("/internal/agent/actions/refund")
                        .header("X-Agent-Ticket", ticket(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"%s"}
                                """.formatted(orderNo)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data.summary.orderNo").value(orderNo))
                .andReturn().getResponse().getContentAsString();
        String refundNo = objectMapper.readTree(refundAction).path("data").path("actionNo").asText();

        mockMvc.perform(post("/api/agent/actions/" + refundNo + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("REFUNDING"));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.order.status").value("REFUNDING"));
    }

    @Test
    void internalActionEndpointRequiresTicket() throws Exception {
        mockMvc.perform(post("/internal/agent/actions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
