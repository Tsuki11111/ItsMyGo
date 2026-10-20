package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.chat.ChatTicketService;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 顾客 ↔ 商家聊天：会话、消息、卡片、未读与越权边界。 */
class ChatControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ChatTicketService ticketService;

    // ── helpers ──

    private long createCategory(String admin) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "CC" + System.nanoTime(), 0, true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createMerchantProduct(String merchantToken, long categoryId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "", "", new BigDecimal("20.00"), 5, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        return user.getId();
    }

    private Map<String, Object> startPayload(long merchantId, Long productId, Long orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchantId", merchantId);
        if (productId != null) body.put("productId", productId);
        if (orderId != null) body.put("orderId", orderId);
        return body;
    }

    private long startConversation(String customer, long merchantId, Long productId, Long orderId)
            throws Exception {
        String body = mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                startPayload(merchantId, productId, orderId))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String send(String token, long conversationId, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn().getResponse().getContentAsString();
    }

    private String sendText(String token, long conversationId, String text) throws Exception {
        return send(token, conversationId, Map.of("type", "TEXT", "text", text));
    }

    private String sendMerchant(String token, long conversationId, Map<String, Object> payload)
            throws Exception {
        return mockMvc.perform(post("/api/merchant/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn().getResponse().getContentAsString();
    }

    private String sendMerchantText(String token, long conversationId, String text) throws Exception {
        return sendMerchant(token, conversationId, Map.of("type", "TEXT", "text", text));
    }

    private void read(String token, long conversationId, long lastMessageId) throws Exception {
        read("/api/chat", token, conversationId, lastMessageId);
    }

    private void readMerchant(String token, long conversationId, long lastMessageId) throws Exception {
        read("/api/merchant/chat", token, conversationId, lastMessageId);
    }

    private void read(String base, String token, long conversationId, long lastMessageId) throws Exception {
        mockMvc.perform(post(base + "/conversations/" + conversationId + "/read")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastMessageId": %d}
                                """.formatted(lastMessageId)))
                .andExpect(jsonPath("$.code").value(200));
    }

    private long customerUnread(String token) throws Exception {
        String body = mockMvc.perform(get("/api/chat/unread-count")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("customerUnread").asLong();
    }

    private long merchantUnread(String token) throws Exception {
        String body = mockMvc.perform(get("/api/merchant/chat/unread-count")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("merchantUnread").asLong();
    }

    private long insertOrder(long userId, long merchantId, String orderNo, String status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setOriginalAmount(new BigDecimal("99.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setReceiver("聊天测试");
        order.setPhone("13800000000");
        order.setAddress("测试地址");
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(1L);
        item.setProductName("订单商品快照");
        item.setProductImage("http://localhost:9000/x.png");
        item.setUnitPrice(new BigDecimal("99.00"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("99.00"));
        item.setCreatedAt(LocalDateTime.now());
        orderItemMapper.insert(item);
        return order.getId();
    }

    // ── tests ──

    @Test
    void startConversationIsIdempotentAndAttachesProductCardOnlyOnce() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天A店" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productId = createMerchantProduct(merchant.token(), categoryId, "聊天商品" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());

        long first = startConversation(customer, merchant.merchantId(), productId, null);
        long second = startConversation(customer, merchant.merchantId(), productId, null);

        assertThat(second).isEqualTo(first);
        mockMvc.perform(get("/api/chat/conversations/" + first + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].contentType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.records[0].card.productId").value(productId))
                .andExpect(jsonPath("$.data.records[0].card.name").isNotEmpty());

        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].lastMessagePreview").value(
                        org.hamcrest.Matchers.startsWith("[商品] ")));
    }

    @Test
    void cannotChatWithOwnShopAndAdminCannotUseCustomerEndpoints() throws Exception {
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天B店" + System.nanoTime());

        mockMvc.perform(post("/api/chat/conversations")
                        .header("Authorization", bearer(merchant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                startPayload(merchant.merchantId(), null, null))))
                .andExpect(jsonPath("$.code").value(400));

        String admin = adminToken("adm" + System.nanoTime());
        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void platformSelfRunChatIsHandledByAdmins() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, 1L, null, null);

        String sent = sendText(customer, conversationId, "平台自营有人吗");
        long customerMessageId = objectMapper.readTree(sent).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/chat/unread-count")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.merchantUnread").value(1));
        mockMvc.perform(get("/api/admin/chat/conversations")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(conversationId))
                .andExpect(jsonPath("$.data.records[0].unread").value(1));

        String reply = mockMvc.perform(post("/api/admin/chat/conversations/"
                        + conversationId + "/messages")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "TEXT", "text": "平台客服在线"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long replyId = objectMapper.readTree(reply).path("data").path("id").asLong();
        assertThat(objectMapper.readTree(reply).path("data").path("senderType").asText())
                .isEqualTo("MERCHANT");

        assertThat(customerUnread(customer)).isEqualTo(1);
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[1].id").value(replyId));

        mockMvc.perform(post("/api/admin/chat/conversations/" + conversationId + "/read")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastMessageId": %d}
                                """.formatted(customerMessageId)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.peerLastReadMessageId").value(customerMessageId));

        // 顾客不能调用管理员消息接口
        mockMvc.perform(get("/api/admin/chat/conversations")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChatOnlyExposesPlatformSelfRunConversations() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天L店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);
        sendText(customer, conversationId, "普通店铺消息");

        mockMvc.perform(get("/api/admin/chat/conversations")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/admin/chat/conversations/" + conversationId)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "TEXT", "text": "越权回复"}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void sendReadAndUnreadFlow() throws Exception {
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天C店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);

        String sent = sendText(customer, conversationId, "你好，在吗");
        long customerMessageId = objectMapper.readTree(sent).path("data").path("id").asLong();

        assertThat(customerUnread(customer)).isZero();
        assertThat(merchantUnread(merchant.token())).isEqualTo(1);

        mockMvc.perform(get("/api/merchant/chat/conversations")
                        .header("Authorization", bearer(merchant.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].unread").value(1))
                .andExpect(jsonPath("$.data.records[0].lastMessagePreview").value("你好，在吗"));

        mockMvc.perform(get("/api/merchant/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(merchant.token())))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(customerMessageId));

        readMerchant(merchant.token(), conversationId, customerMessageId);
        assertThat(merchantUnread(merchant.token())).isZero();

        String reply = sendMerchantText(merchant.token(), conversationId, "在的");
        long replyId = objectMapper.readTree(reply).path("data").path("id").asLong();
        assertThat(customerUnread(customer)).isEqualTo(1);

        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[1].id").value(replyId))
                // 商家已经读过顾客那条消息
                .andExpect(jsonPath("$.data.peerLastReadMessageId").value(customerMessageId));

        read(customer, conversationId, replyId);
        assertThat(customerUnread(customer)).isZero();
    }

    @Test
    void messageContentIsValidated() throws Exception {
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天D店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);

        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "TEXT", "text", "   "))).path("code").asInt()).isEqualTo(400);
        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "TEXT", "text", "x".repeat(2001)))).path("code").asInt()).isEqualTo(400);
        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "IMAGE", "imageUrl", "https://evil.example/x.png")))
                .path("code").asInt()).isEqualTo(400);
        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "BOGUS"))).path("code").asInt()).isEqualTo(400);

        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void productCardMustBelongToTheConversationShop() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("ca" + System.nanoTime(), "聊天E店" + System.nanoTime());
        MerchantSession b = merchantSession("cb" + System.nanoTime(), "聊天F店" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productB = createMerchantProduct(b.token(), categoryId, "别家商品" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, a.merchantId(), null, null);

        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "PRODUCT", "productId", productB))).path("code").asInt())
                .isEqualTo(404);
    }

    @Test
    void orderCardIsFrozenAndValidatesOwnership() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天G店" + System.nanoTime());
        String customerName = "cc" + System.nanoTime();
        String customer = customerToken(customerName);
        long customerId = userId(customerName);
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);

        long orderId = insertOrder(customerId, merchant.merchantId(),
                "CHAT" + System.nanoTime(), "PENDING_PAYMENT");
        send(customer, conversationId, Map.of("type", "ORDER", "orderId", orderId));
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records[0].contentType").value("ORDER"))
                .andExpect(jsonPath("$.data.records[0].card.orderId").value(orderId))
                .andExpect(jsonPath("$.data.records[0].card.statusLabel").value("待支付"));

        // 订单状态后续变化不影响已发送的卡片快照
        Order changed = new Order();
        changed.setId(orderId);
        changed.setStatus("SHIPPED");
        orderMapper.updateById(changed);
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records[0].card.status").value("PENDING_PAYMENT"));

        // 别人的订单不能发到这条会话
        String otherName = "cc" + System.nanoTime();
        customerToken(otherName);
        long otherOrder = insertOrder(userId(otherName), merchant.merchantId(),
                "CHAT" + System.nanoTime(), "PENDING_PAYMENT");
        assertThat(objectMapper.readTree(send(customer, conversationId,
                Map.of("type", "ORDER", "orderId", otherOrder))).path("code").asInt())
                .isEqualTo(404);
    }

    @Test
    void crossUserAndCrossMerchantAccessReturns404() throws Exception {
        MerchantSession a = merchantSession("ca" + System.nanoTime(), "聊天H店" + System.nanoTime());
        MerchantSession b = merchantSession("cb" + System.nanoTime(), "聊天I店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        String otherCustomer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, a.merchantId(), null, null);
        sendText(customer, conversationId, "只给 A 店看");

        mockMvc.perform(get("/api/chat/conversations/" + conversationId)
                        .header("Authorization", bearer(otherCustomer)))
                .andExpect(jsonPath("$.code").value(404));
        assertThat(objectMapper.readTree(sendText(otherCustomer, conversationId, "越权"))
                .path("code").asInt()).isEqualTo(404);

        mockMvc.perform(get("/api/merchant/chat/conversations/" + conversationId)
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.code").value(404));
        assertThat(objectMapper.readTree(sendMerchantText(b.token(), conversationId, "越权"))
                .path("code").asInt()).isEqualTo(404);

        mockMvc.perform(get("/api/merchant/chat/conversations")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void disabledShopBlocksSendingButKeepsHistory() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天J店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);
        sendText(customer, conversationId, "停用前");

        mockMvc.perform(post("/api/admin/merchants/" + merchant.merchantId() + "/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        assertThat(objectMapper.readTree(sendText(customer, conversationId, "停用后"))
                .path("code").asInt()).isEqualTo(400);
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    void messagePaginationAndAfterIdCatchUp() throws Exception {
        MerchantSession merchant = merchantSession("cm" + System.nanoTime(), "聊天K店" + System.nanoTime());
        String customer = customerToken("cc" + System.nanoTime());
        long conversationId = startConversation(customer, merchant.merchantId(), null, null);
        long[] ids = new long[5];
        for (int i = 1; i <= 5; i++) {
            String body = sendText(customer, conversationId, "第 " + i + " 条");
            ids[i - 1] = objectMapper.readTree(body).path("data").path("id").asLong();
        }

        String page = mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer))
                        .param("size", "2"))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        long oldestOnPage = objectMapper.readTree(page)
                .path("data").path("records").get(0).path("id").asLong();

        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer))
                        .param("beforeId", String.valueOf(oldestOnPage))
                        .param("size", "2"))
                .andExpect(jsonPath("$.data.records.length()").value(2));

        // 断线补齐：afterId 之后的消息（第 2 条之后共 3 条）
        mockMvc.perform(get("/api/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(customer))
                        .param("afterId", String.valueOf(ids[1])))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @Test
    void streamTicketIsShortLivedAndOneTime() throws Exception {
        var ticket = ticketService.issue(123L);
        assertThat(ticket.expiresInSeconds()).isEqualTo(60);
        Long ttl = redisTemplate.getExpire("its-my-go:chat:ticket:" + ticket.ticket());
        assertThat(ttl).isNotNull().isBetween(1L, 60L);

        assertThat(ticketService.consume(ticket.ticket())).isEqualTo(123L);
        assertThat(ticketService.consume(ticket.ticket())).isNull();

        mockMvc.perform(get("/api/chat/stream").param("ticket", "not-a-ticket"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/chat/stream-ticket"))
                .andExpect(status().isUnauthorized());
        String admin = adminToken("adm" + System.nanoTime());
        mockMvc.perform(post("/api/chat/stream-ticket")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ticket").isNotEmpty());
    }

    @Test
    void customerEndpointsRequireLoginAndMerchantEndpointsRequireMerchantRole() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized());

        String customer = customerToken("cc" + System.nanoTime());
        mockMvc.perform(get("/api/merchant/chat/conversations")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());

        CurrentUser user = new CurrentUser(1L, "u", Roles.CUSTOMER, null);
        assertThat(user.isMerchant()).isFalse();
    }
}
