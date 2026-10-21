package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.Shipment;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ShipmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 虚拟物流：发货生成运单、ETA 到点即送达、归属校验、agent 能查到物流摘要。 */
class OrderLogisticsControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ShipmentMapper shipmentMapper;

    @Autowired
    private AgentToolTicketService ticketService;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private Order insertPaidOrder(long userId, String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(1L);
        order.setTotalAmount(new BigDecimal("66.00"));
        order.setOriginalAmount(new BigDecimal("66.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponName("");
        order.setStatus("PAID");
        order.setReceiver("张三");
        order.setPhone("13800000000");
        order.setAddress("浙江省杭州市西湖区文三路 1 号");
        order.setTrackingNo("");
        order.setPaidAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }

    private String ship(String admin, long orderId) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"SFLOGISTICS001"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void shipCreatesVirtualShipmentAndCustomerCanReadIt() throws Exception {
        String username = "lg" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "LG" + System.nanoTime();
        Order order = insertPaidOrder(uid, orderNo);
        String admin = adminToken("adm" + System.nanoTime());

        ship(admin, order.getId());

        mockMvc.perform(get("/api/orders/" + order.getId() + "/logistics")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.trackingNo").value("SFLOGISTICS001"))
                .andExpect(jsonPath("$.data.carrier").isNotEmpty())
                .andExpect(jsonPath("$.data.courierName").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.statusLabel").value("已揽收"))
                .andExpect(jsonPath("$.data.eta").isNotEmpty())
                .andExpect(jsonPath("$.data.originName").value(endsWith("转运中心")))
                .andExpect(jsonPath("$.data.timeline.length()").value(4))
                .andExpect(jsonPath("$.data.destAddress").value("浙江省杭州市西湖区文三路 1 号"));

        // agent 的订单工具也要能拿到物流摘要，但不应包含地址
        String ticket = ticketService.issue(uid, "sess-logistics");
        mockMvc.perform(post("/internal/agent/orders/query")
                        .header("X-Agent-Ticket", ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"%s"}
                                """.formatted(orderNo)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].logistics.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data[0].logistics.eta").isNotEmpty())
                .andExpect(jsonPath("$.data[0].logistics.carrier").isNotEmpty())
                .andExpect(jsonPath("$.data[0].address").doesNotExist());
    }

    @Test
    void etaBecomesActualDeliveryTime() throws Exception {
        String username = "le" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String orderNo = "LE" + System.nanoTime();
        Order order = insertPaidOrder(uid, orderNo);
        String admin = adminToken("adm" + System.nanoTime());
        ship(admin, order.getId());

        Shipment shipment = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getOrderId, order.getId()));
        LocalDateTime past = LocalDateTime.now().minusMinutes(200).withNano(0);
        shipment.setShippedAt(past);
        shipment.setEta(past.plusMinutes(120));
        shipment.setStatus("PICKED_UP");
        shipment.setDeliveredAt(null);
        shipmentMapper.updateById(shipment);

        mockMvc.perform(get("/api/orders/" + order.getId() + "/logistics")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.statusLabel").value("已送达"))
                .andExpect(jsonPath("$.data.progress").value(100))
                .andExpect(jsonPath("$.data.deliveredAt").value(
                        shipment.getEta().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        Shipment saved = shipmentMapper.selectById(shipment.getId());
        assertThat(saved.getStatus()).isEqualTo("DELIVERED");
        assertThat(saved.getDeliveredAt()).isEqualTo(shipment.getEta());
    }

    @Test
    void unshippedOrderHasNoLogisticsAndAnotherUserIsRejected() throws Exception {
        String username = "lu" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        Order order = insertPaidOrder(uid, "LU" + System.nanoTime());

        mockMvc.perform(get("/api/orders/" + order.getId() + "/logistics")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        String other = customerToken("lx" + System.nanoTime());
        mockMvc.perform(get("/api/orders/" + order.getId() + "/logistics")
                        .header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void platformMerchantShipAddressBecomesLogisticsOrigin() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        mockMvc.perform(put("/api/admin/merchants/1/ship-address")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"province":"浙江省","city":"杭州市","district":"西湖区",
                                 "address":"文三路 1 号","lng":120.123456,"lat":30.654321}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shipProvince").value("浙江省"))
                .andExpect(jsonPath("$.data.shipCity").value("杭州市"))
                .andExpect(jsonPath("$.data.shipLng").value(120.123456));

        String username = "lgo" + System.nanoTime();
        String token = customerToken(username);
        Order order = insertPaidOrder(userId(username), "LGO" + System.nanoTime());
        ship(admin, order.getId());

        mockMvc.perform(get("/api/orders/" + order.getId() + "/logistics")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.originName").value("平台自营发货仓"))
                .andExpect(jsonPath("$.data.originLng").value(120.123456))
                .andExpect(jsonPath("$.data.originLat").value(30.654321));
    }

    @Test
    void merchantSetsOwnShipAddressAndCanClearIt() throws Exception {
        MerchantSession session = merchantSession(
                "ms" + System.nanoTime(), "发货店" + System.nanoTime());
        String body = """
                {"province":"浙江省","city":"杭州市","district":"西湖区",
                 "address":"文三路 9 号","lng":120.1,"lat":30.2}
                """;

        mockMvc.perform(put("/api/merchant/ship-address")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shipProvince").value("浙江省"))
                .andExpect(jsonPath("$.data.shipAddress").value("文三路 9 号"))
                .andExpect(jsonPath("$.data.shipLng").value(120.1))
                .andExpect(jsonPath("$.data.shipLat").value(30.2));

        // 顾客无权设置商家发货地址
        String customer = customerToken("mc" + System.nanoTime());
        mockMvc.perform(put("/api/merchant/ship-address")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(403));

        // 清空地址与坐标后回退虚拟转运中心（坐标为 null 而不是保留旧值）
        mockMvc.perform(put("/api/merchant/ship-address")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"province":"","city":"","district":"","address":"","lng":null,"lat":null}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shipAddress").value(""))
                .andExpect(jsonPath("$.data.shipLng").value(nullValue()))
                .andExpect(jsonPath("$.data.shipLat").value(nullValue()));
    }
}
