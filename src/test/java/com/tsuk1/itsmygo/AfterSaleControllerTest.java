package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.AfterSaleApplyRequest;
import com.tsuk1.itsmygo.dto.AfterSaleItemRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonRequest;
import com.tsuk1.itsmygo.dto.AfterSaleRejectRequest;
import com.tsuk1.itsmygo.dto.AfterSaleShipRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 售后（M30）端到端用例：申请 / 审核 / 部分退款金额 / 整单退完 / 退货退款 / 撤销 / 越权。
 */
class AfterSaleControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    /* ==================== 造数 ==================== */

    private long addressId(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "Receiver", "13800000000", "P", "C", "D", "Detail 1", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addToCart(String token, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, defaultSkuId(productId), quantity))))
                .andExpect(status().isOk());
    }

    private String orderToken(String token) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    private long placeCartOrder(String token, long addressId) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, orderToken(token), null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("order").path("id").asLong();
    }

    private void pay(String token, long orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void ship(String adminToken, long orderId) throws Exception {
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShipRequest("SF" + orderId))))
                .andExpect(status().isOk());
    }

    private long apply(String token, long orderId, String type, long orderItemId, int quantity)
            throws Exception {
        String body = mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AfterSaleApplyRequest(
                                orderId, type, "QUALITY", "测试原因", List.of(),
                                List.of(new AfterSaleItemRequest(orderItemId, quantity))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private int skuStock(long productId) {
        ProductSku sku = productSkuMapper.selectOne(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getSpecValueIds, ""));
        return sku.getStock();
    }

    private Order order(long orderId) {
        return orderMapper.selectById(orderId);
    }

    /* ==================== 用例 ==================== */

    @Test
    void reasonsAreSeeded() throws Exception {
        String token = customerToken("as_reason" + System.nanoTime());
        mockMvc.perform(get("/api/after-sales/reasons").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.data[0].code").exists());
    }

    @Test
    void afterSaleResultNotifiesBuyer() throws Exception {
        String admin = adminToken("as_notify_admin" + System.nanoTime());
        String token = customerToken("as_notify" + System.nanoTime());
        long product = createProductForTest(admin, "AS-N" + System.nanoTime(), "80.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long targetItem = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();

        long afterSaleId = apply(token, orderId, "REFUND_ONLY", targetItem, 1);
        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("AFTER_SALE_RESULT"))
                .andExpect(jsonPath("$.data.items[0].title").value("售后处理结果"))
                .andExpect(jsonPath("$.data.items[0].link").value("/after-sale/" + afterSaleId));
    }

    @Test
    void partialRefundKeepsOrderAliveAndRestoresStock() throws Exception {
        String admin = adminToken("as_admin" + System.nanoTime());
        String token = customerToken("as_partial" + System.nanoTime());
        long productA = createProductForTest(admin, "AS-A" + System.nanoTime(), "100.00", 10);
        long productB = createProductForTest(admin, "AS-B" + System.nanoTime(), "50.00", 10);
        addToCart(token, productA, 1);
        addToCart(token, productB, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);

        // 按 productId 找目标订单项（商品名带 nano 后缀，不能按名字匹配）
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(body).path("data").path("items");
        long targetItem = -1;
        for (var item : items) {
            if (item.path("productId").asLong() == productB) {
                targetItem = item.path("id").asLong();
            }
        }
        assertThat(targetItem).isPositive();

        long afterSaleId = apply(token, orderId, "REFUND_ONLY", targetItem, 1);
        mockMvc.perform(get("/api/after-sales/" + afterSaleId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.applyAmount").value(50.0))
                .andExpect(jsonPath("$.data.canCancel").value(true));

        // 商家/平台同意（用管理端介入接口）
        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundAmount").value(50.0));

        // 部分退款：订单仍然有效（PAID），累计已退 50
        Order after = order(orderId);
        assertThat(after.getStatus()).isEqualTo("PAID");
        assertThat(after.getRefundedAmount()).isEqualByComparingTo("50.00");
        // 库存回补到 10
        assertThat(skuStock(productB)).isEqualTo(10);
        // 支付流水未退款（只退了一部分）
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId));
        assertThat(payment.getStatus()).isEqualTo(Payment.STATUS_SUCCESS);
    }

    @Test
    void refundingAllItemsCompletesOrder() throws Exception {
        String admin = adminToken("as_admin2" + System.nanoTime());
        String token = customerToken("as_full" + System.nanoTime());
        long productA = createProductForTest(admin, "AS2-A" + System.nanoTime(), "100.00", 10);
        long productB = createProductForTest(admin, "AS2-B" + System.nanoTime(), "50.00", 10);
        addToCart(token, productA, 1);
        addToCart(token, productB, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);

        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(body).path("data").path("items");
        long itemA = -1;
        long itemB = -1;
        for (var item : items) {
            if (item.path("productId").asLong() == productA) {
                itemA = item.path("id").asLong();
            } else {
                itemB = item.path("id").asLong();
            }
        }
        long first = apply(token, orderId, "REFUND_ONLY", itemB, 1);
        mockMvc.perform(post("/api/admin/after-sales/" + first + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        long second = apply(token, orderId, "REFUND_ONLY", itemA, 1);
        mockMvc.perform(post("/api/admin/after-sales/" + second + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        Order after = order(orderId);
        assertThat(after.getStatus()).isEqualTo("REFUNDED");
        assertThat(after.getRefundedAmount()).isEqualByComparingTo("150.00");
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId));
        assertThat(payment.getStatus()).isEqualTo(Payment.STATUS_REFUNDED);
    }

    @Test
    void discountIsAllocatedProportionally() throws Exception {
        String admin = adminToken("as_admin3" + System.nanoTime());
        String token = customerToken("as_disc" + System.nanoTime());
        long productA = createProductForTest(admin, "AS3-A" + System.nanoTime(), "100.00", 10);
        long productB = createProductForTest(admin, "AS3-B" + System.nanoTime(), "50.00", 10);
        addToCart(token, productA, 1);
        addToCart(token, productB, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        // 人为制造 9 折：原价 150、实付 135、优惠 15
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .set(Order::getOriginalAmount, new BigDecimal("150.00"))
                .set(Order::getTotalAmount, new BigDecimal("135.00"))
                .set(Order::getDiscountAmount, new BigDecimal("15.00")));

        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(body).path("data").path("items");
        long itemB = -1;
        for (var item : items) {
            if (item.path("productId").asLong() == productB) {
                itemB = item.path("id").asLong();
            }
        }
        long afterSaleId = apply(token, orderId, "REFUND_ONLY", itemB, 1);
        mockMvc.perform(get("/api/after-sales/" + afterSaleId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.applyAmount").value(45.0));
    }

    @Test
    void returnRefundFlow() throws Exception {
        String admin = adminToken("as_admin4" + System.nanoTime());
        String token = customerToken("as_return" + System.nanoTime());
        long product = createProductForTest(admin, "AS4-P" + System.nanoTime(), "80.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        ship(admin, orderId);

        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();

        long afterSaleId = apply(token, orderId, "RETURN_REFUND", itemId, 1);
        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_RETURN"));

        mockMvc.perform(post("/api/after-sales/" + afterSaleId + "/ship-back")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AfterSaleShipRequest("SF-RETURN-1", "顺丰"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURN_SHIPPED"));

        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/confirm-received")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        assertThat(order(orderId).getStatus()).isEqualTo("REFUNDED");
        assertThat(skuStock(product)).isEqualTo(5);
    }

    @Test
    void returnRefundRequiresShippedOrder() throws Exception {
        String admin = adminToken("as_admin5" + System.nanoTime());
        String token = customerToken("as_cannot" + System.nanoTime());
        long product = createProductForTest(admin, "AS5-P" + System.nanoTime(), "60.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);

        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();

        mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AfterSaleApplyRequest(
                                orderId, "RETURN_REFUND", "QUALITY", "", List.of(),
                                List.of(new AfterSaleItemRequest(itemId, 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectRequiresReasonAndCanReapply() throws Exception {
        String admin = adminToken("as_admin6" + System.nanoTime());
        String token = customerToken("as_reject" + System.nanoTime());
        long product = createProductForTest(admin, "AS6-P" + System.nanoTime(), "70.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
        long afterSaleId = apply(token, orderId, "REFUND_ONLY", itemId, 1);

        // 驳回没填理由 → 400
        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/reject")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/admin/after-sales/" + afterSaleId + "/reject")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AfterSaleRejectRequest("不符合条件"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("不符合条件"));
        // 驳回后可以再次申请（不占用数量）
        apply(token, orderId, "REFUND_ONLY", itemId, 1);
    }

    @Test
    void cancelThenReapply() throws Exception {
        String admin = adminToken("as_admin7" + System.nanoTime());
        String token = customerToken("as_cancel" + System.nanoTime());
        long product = createProductForTest(admin, "AS7-P" + System.nanoTime(), "30.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
        long afterSaleId = apply(token, orderId, "REFUND_ONLY", itemId, 1);
        mockMvc.perform(post("/api/after-sales/" + afterSaleId + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        apply(token, orderId, "REFUND_ONLY", itemId, 1);
    }

    @Test
    void overRefundIsBlocked() throws Exception {
        String admin = adminToken("as_admin8" + System.nanoTime());
        String token = customerToken("as_over" + System.nanoTime());
        long product = createProductForTest(admin, "AS8-P" + System.nanoTime(), "20.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
        apply(token, orderId, "REFUND_ONLY", itemId, 1);

        mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AfterSaleApplyRequest(
                                orderId, "REFUND_ONLY", "QUALITY", "", List.of(),
                                List.of(new AfterSaleItemRequest(itemId, 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void othersCannotViewOrHandleAfterSale() throws Exception {
        String admin = adminToken("as_admin9" + System.nanoTime());
        String token = customerToken("as_owner" + System.nanoTime());
        String other = customerToken("as_other" + System.nanoTime());
        long product = createProductForTest(admin, "AS9-P" + System.nanoTime(), "40.00", 5);
        addToCart(token, product, 1);
        long address = addressId(token);
        long orderId = placeCartOrder(token, address);
        pay(token, orderId);
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
        long afterSaleId = apply(token, orderId, "REFUND_ONLY", itemId, 1);

        mockMvc.perform(get("/api/after-sales/" + afterSaleId).header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void adminManagesReasonDictionary() throws Exception {
        String admin = adminToken("as_admin10" + System.nanoTime());
        String code = "R" + System.nanoTime() % 1_000_000;
        String body = mockMvc.perform(post("/api/admin/after-sale-reasons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AfterSaleReasonRequest(code, "测试原因", 5, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("data").path("id").asLong();

        // 重复 code → 400
        mockMvc.perform(post("/api/admin/after-sale-reasons")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AfterSaleReasonRequest(code, "重复", 0, 1))))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/admin/after-sale-reasons/" + id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AfterSaleReasonRequest(code, "改名", 5, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }
}
