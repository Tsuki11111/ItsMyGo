package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 商家看板：只统计本店；金额、订单、待办口径与后台一致。 */
class MerchantStatsControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    private void insertOrder(long merchantId, String orderNo, String amount, String status,
                             boolean paid) {
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setMerchantId(merchantId);
        order.setTotalAmount(new BigDecimal(amount));
        order.setOriginalAmount(new BigDecimal(amount));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setReceiver("测试收货人");
        order.setPhone("13800000000");
        order.setAddress("测试地址");
        order.setCreatedAt(now);
        if (paid) {
            order.setPaidAt(now);
        }
        orderMapper.insert(order);
    }

    @Test
    void merchantSeesOnlyOwnStoreStats() throws Exception {
        MerchantSession session = merchantSession(
                "mstat" + System.nanoTime(), "统计测试店" + System.nanoTime());
        insertOrder(session.merchantId(), "STAT" + System.nanoTime(), "100.00", "PAID", true);
        insertOrder(session.merchantId(), "STAT" + System.nanoTime(), "50.00", "REFUNDING", true);
        insertOrder(session.merchantId(), "STAT" + System.nanoTime(), "30.00", "CANCELLED", false);

        mockMvc.perform(get("/api/merchant/stats")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantName").value(org.hamcrest.Matchers.containsString("统计测试店")))
                .andExpect(jsonPath("$.data.todayGmv").value("150.00"))
                .andExpect(jsonPath("$.data.todayOrders").value(3))
                .andExpect(jsonPath("$.data.gmv30").value("150.00"))
                .andExpect(jsonPath("$.data.paidOrders30").value(2))
                .andExpect(jsonPath("$.data.orders30").value(3))
                .andExpect(jsonPath("$.data.refunding30").value(1))
                .andExpect(jsonPath("$.data.avgOrderValue30").value("75.00"))
                .andExpect(jsonPath("$.data.pendingShipment").value(1))
                .andExpect(jsonPath("$.data.pendingRefund").value(1))
                .andExpect(jsonPath("$.data.trend.length()").value(7))
                .andExpect(jsonPath("$.data.recentOrders.length()").value(3));
    }

    @Test
    void customerCannotReadMerchantStats() throws Exception {
        String customer = customerToken("cstat" + System.nanoTime());
        mockMvc.perform(get("/api/merchant/stats")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
