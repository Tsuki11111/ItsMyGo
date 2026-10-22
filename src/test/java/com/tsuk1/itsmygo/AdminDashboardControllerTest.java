package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 管理后台概览：指标、7 天趋势、待发货/待退款待办与低库存。 */
class AdminDashboardControllerTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    private void insertOrder(long userId, String orderNo, String status) {
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
        order.setPaidAt(now);
        if ("REFUNDING".equals(status)) {
            order.setStatusBeforeRefund("PAID");
        }
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
    }

    @Test
    void dashboardShowsMetricsAndTodos() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "db" + System.nanoTime();
        customerToken(username);
        long uid = userId(username);

        String paidOrderNo = "DBP" + System.nanoTime();
        insertOrder(uid, paidOrderNo, "PAID");
        String refundOrderNo = "DBR" + System.nanoTime();
        insertOrder(uid, refundOrderNo, "REFUNDING");

        String productName = "低库存测试商品" + System.nanoTime();
        createProductForTest(admin, productName, "10.00", 3);

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.todayGmv").isNotEmpty())
                .andExpect(jsonPath("$.data.todayOrders").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.todayPaidOrders").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.pendingShipments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.pendingRefunds").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.lowStockSkus").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.trend.length()").value(7))
                .andExpect(jsonPath("$.data.pendingShipmentOrders[*].orderNo")
                        .value(hasItem(paidOrderNo)))
                .andExpect(jsonPath("$.data.pendingRefundOrders[*].orderNo")
                        .value(hasItem(refundOrderNo)))
                .andExpect(jsonPath("$.data.lowStockList.length()")
                        .value(lessThanOrEqualTo(5)));
    }
}
