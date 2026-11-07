package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** agent 内部工具接口：票据鉴权、用户归属、最小化字段。 */
class AgentToolControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AgentToolTicketService ticketService;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private String ticket(long userId) {
        return ticketService.issue(userId, "sess-test");
    }

    private void insertOrder(long userId, String orderNo, String status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(1L);
        order.setTotalAmount(new BigDecimal("199.00"));
        order.setOriginalAmount(new BigDecimal("199.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponName("");
        order.setStatus(status);
        order.setReceiver("张三");
        order.setPhone("13800000000");
        order.setAddress("杭州市西湖区文三路 1 号");
        order.setTrackingNo("SF1234567890");
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setProductName("测试商品");
        item.setProductImage("");
        item.setSpecText("颜色:白色");
        item.setUnitPrice(new BigDecimal("99.50"));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("199.00"));
        orderItemMapper.insert(item);
    }

    @Test
    void queriesOwnOrderByOrderNoAndHidesPii() throws Exception {
        String username = "oq" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "NO" + System.nanoTime();
        insertOrder(uid, orderNo, "PAID");

        mockMvc.perform(post("/internal/agent/orders/query")
                        .header("X-Agent-Ticket", ticket(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"%s"}
                                """.formatted(orderNo)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data[0].status").value("PAID"))
                .andExpect(jsonPath("$.data[0].statusLabel").value("待发货"))
                .andExpect(jsonPath("$.data[0].totalAmount").value("199.00"))
                .andExpect(jsonPath("$.data[0].trackingNo").value("SF1234567890"))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("测试商品"))
                .andExpect(jsonPath("$.data[0].items[0].specText").value("颜色:白色"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                // 收货人 / 电话 / 地址不出现在 agent 响应里
                .andExpect(jsonPath("$.data[0].receiver").doesNotExist())
                .andExpect(jsonPath("$.data[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data[0].address").doesNotExist())
                .andExpect(content().string(not(containsString("13800000000"))))
                .andExpect(content().string(not(containsString("杭州市西湖区"))));
    }

    @Test
    void cannotQueryAnotherUsersOrder() throws Exception {
        String aName = "oqa" + System.nanoTime();
        String bName = "oqb" + System.nanoTime();
        customerToken(aName);
        customerToken(bName);
        long aId = userId(aName);
        long bId = userId(bName);
        String bOrderNo = "BNO" + System.nanoTime();
        insertOrder(bId, bOrderNo, "PAID");

        mockMvc.perform(post("/internal/agent/orders/query")
                        .header("X-Agent-Ticket", ticket(aId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"%s"}
                                """.formatted(bOrderNo)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void recentOrdersAreScopedAndLimited() throws Exception {
        String aName = "ora" + System.nanoTime();
        String bName = "orb" + System.nanoTime();
        customerToken(aName);
        customerToken(bName);
        long aId = userId(aName);
        long bId = userId(bName);
        String a1 = "A1" + System.nanoTime();
        String a2 = "A2" + System.nanoTime();
        String a3 = "A3" + System.nanoTime();
        String b1 = "B1" + System.nanoTime();
        insertOrder(aId, a1, "PAID");
        insertOrder(aId, a2, "SHIPPED");
        insertOrder(aId, a3, "COMPLETED");
        insertOrder(bId, b1, "PAID");

        mockMvc.perform(post("/internal/agent/orders/query")
                        .header("X-Agent-Ticket", ticket(aId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"limit":2}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(content().string(not(containsString(b1))))
                .andExpect(content().string(containsString(a3)))
                .andExpect(content().string(containsString(a2)));
    }

    @Test
    void invalidTicketIsRejected() throws Exception {
        mockMvc.perform(post("/internal/agent/orders/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/internal/agent/orders/query")
                        .header("X-Agent-Ticket", "not-a-real-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(401));
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

    @Test
    void addressEditUpdatesOwnedAddressAndKeepsDefaultFlag() throws Exception {
        String username = "ad" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        long addressId = createAddress(token);

        mockMvc.perform(post("/internal/agent/actions/address")
                        .header("X-Agent-Ticket", ticket(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":%d,"receiver":"李四","phone":"13900000000",
                                 "province":"浙江省","city":"杭州市","district":"西湖区",
                                 "detail":"文三路 2 号"}
                                """.formatted(addressId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary.address.id").value(addressId))
                .andExpect(jsonPath("$.data.summary.address.receiver").value("李四"))
                .andExpect(jsonPath("$.data.summary.address.address")
                        .value("浙江省杭州市西湖区文三路 2 号"));

        // 内部工具不传 isDefault，不能把用户原来的默认地址标记清掉
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].receiver").value("李四"))
                .andExpect(jsonPath("$.data[0].isDefault").value(1));
    }

    @Test
    void addressEditRejectsAnotherUsersAddress() throws Exception {
        String aName = "ada" + System.nanoTime();
        String bName = "adb" + System.nanoTime();
        String bToken = customerToken(bName);
        customerToken(aName);
        long aId = userId(aName);
        long bAddressId = createAddress(bToken);

        mockMvc.perform(post("/internal/agent/actions/address")
                        .header("X-Agent-Ticket", ticket(aId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":%d,"receiver":"李四","phone":"13900000000",
                                 "province":"浙江省","city":"杭州市","district":"西湖区",
                                 "detail":"文三路 2 号"}
                                """.formatted(bAddressId)))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void addressEditValidatesParamsAndTicket() throws Exception {
        String username = "adv" + System.nanoTime();
        customerToken(username);
        long uid = userId(username);

        // 手机号非法
        mockMvc.perform(post("/internal/agent/actions/address")
                        .header("X-Agent-Ticket", ticket(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":1,"receiver":"李四","phone":"123",
                                 "province":"浙江省","city":"杭州市","district":"西湖区",
                                 "detail":"文三路 2 号"}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 缺地址 id
        mockMvc.perform(post("/internal/agent/actions/address")
                        .header("X-Agent-Ticket", ticket(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiver":"李四","phone":"13900000000",
                                 "province":"浙江省","city":"杭州市","district":"西湖区",
                                 "detail":"文三路 2 号"}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 没有票据：参数合法也要 401（鉴权先于业务）
        mockMvc.perform(post("/internal/agent/actions/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId":1,"receiver":"李四","phone":"13900000000",
                                 "province":"浙江省","city":"杭州市","district":"西湖区",
                                 "detail":"文三路 2 号"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }
}
