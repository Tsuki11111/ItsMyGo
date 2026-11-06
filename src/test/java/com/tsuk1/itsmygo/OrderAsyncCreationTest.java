package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.order.OrderCreateProperties;
import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.service.OrderSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 下单异步补账（M31 / D1）的状态与结算语义测试。
 *
 * <p>测试 profile 用 {@code async=false}（同步降级），所以下单后订单应当直接是
 * PENDING_PAYMENT；这里另外覆盖「创建中/创建失败」两个新状态的守卫与补账幂等。</p>
 */
class OrderAsyncCreationTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderSettlementService orderSettlementService;

    @Autowired
    private OrderCreateProperties orderCreateProperties;

    private String prepareOrder(String prefix) throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken(prefix + System.nanoTime());
        long productId = createProductForTest(admin, prefix + System.nanoTime(), "100.00", 5);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, defaultSkuId(productId), 1))))
                .andExpect(status().isOk());
        String addressBody = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "Receiver", "13800000000", "P", "C", "D", "Detail", true))))
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(addressBody).path("data").path("id").asLong();
        String tokenBody = mockMvc.perform(get("/api/orders/token")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        String orderToken = objectMapper.readTree(tokenBody).path("data").asText();
        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(addressId, orderToken, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("order").path("id").asLong();
        return token + "|" + orderId + "|" + productId;
    }

    private static String[] split(String prepared) {
        return prepared.split("\\|");
    }

    private Order order(long orderId) {
        return orderMapper.selectById(orderId);
    }

    private int skuStock(long productId) {
        ProductSku sku = productSkuMapper.selectOne(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getSpecValueIds, ""));
        return sku.getStock();
    }

    @Test
    void testProfileUsesSyncDegradeMode() {
        assertThat(orderCreateProperties.async()).isFalse();
    }

    @Test
    void syncDegradeSettlesOrderImmediatelyAndDeductsStock() throws Exception {
        String[] ctx = split(prepareOrder("m31sync"));
        Order created = order(Long.parseLong(ctx[1]));
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(skuStock(Long.parseLong(ctx[2]))).isEqualTo(4);
    }

    @Test
    void settlementIsIdempotent() throws Exception {
        String[] ctx = split(prepareOrder("m31idem"));
        long orderId = Long.parseLong(ctx[1]);
        int stockAfterCreate = skuStock(Long.parseLong(ctx[2]));

        // 已经是 PENDING_PAYMENT，再次补账应该什么也不做
        assertThat(orderSettlementService.settle(java.util.List.of(orderId), java.util.List.of()))
                .isEmpty();
        assertThat(skuStock(Long.parseLong(ctx[2]))).isEqualTo(stockAfterCreate);
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
    }

    @Test
    void creatingOrderCannotBePaid() throws Exception {
        String[] ctx = split(prepareOrder("m31pay"));
        long orderId = Long.parseLong(ctx[1]);
        orderMapper.compareAndSetStatus(orderId, OrderStatus.PENDING_PAYMENT.name(),
                OrderStatus.CREATING.name());

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(ctx[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("创建中")));
    }

    @Test
    void creatingOrderCannotBeCancelled() throws Exception {
        String[] ctx = split(prepareOrder("m31cancel"));
        long orderId = Long.parseLong(ctx[1]);
        orderMapper.compareAndSetStatus(orderId, OrderStatus.PENDING_PAYMENT.name(),
                OrderStatus.CREATING.name());

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(ctx[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("创建中")));
    }

    @Test
    void failCreationMarksOrderFailedWithReason() throws Exception {
        String[] ctx = split(prepareOrder("m31fail"));
        long orderId = Long.parseLong(ctx[1]);
        orderMapper.compareAndSetStatus(orderId, OrderStatus.PENDING_PAYMENT.name(),
                OrderStatus.CREATING.name());

        orderSettlementService.failCreation(java.util.List.of(orderId), "模拟补账失败");

        Order failed = order(orderId);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.CREATE_FAILED.name());
        assertThat(failed.getCreateError()).contains("模拟补账失败");
        // 创建失败后不能支付，提示重新下单
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(ctx[0])))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("重新下单")));
    }
}
