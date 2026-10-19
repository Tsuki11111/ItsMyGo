package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 商家评价隔离：本店评价可见、只能隐藏本店评价 */
class MerchantReviewTest extends ApiTestBase {

    private long createCategory(String admin) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "RC" + System.nanoTime(), 0, true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createProduct(String merchantToken, long categoryId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "", "", new BigDecimal("20.00"), 5, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createAddress(String customer) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiver":"评价测试","phone":"13800000000","province":"广东省",
                                 "city":"深圳市","district":"南山区","detail":"科技园 1 号","isDefault":true}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 买一件、支付、管理端发货、顾客确认收货，返回 orderItemId */
    private long completedOrderItem(String customer, String admin, long productId) throws Exception {
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(customer);
        String token = objectMapper.readTree(mockMvc.perform(get("/api/orders/token")
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString()).path("data").asText();

        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId": %d, "token": "%s",
                                 "items": [{"productId": %d, "skuId": %d, "quantity": 1}],
                                 "coupons": []}
                                """.formatted(addressId, token, productId, skuId)))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("order").path("id").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/ship")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo": "SFMR001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/confirm")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        String detail = mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(detail).path("data").path("items").get(0).path("id").asLong();
    }

    private void review(String customer, long orderItemId) throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": 5, "content": "商家评价测试"}
                                """.formatted(orderItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void merchantOnlySeesAndHidesOwnReviews() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("ra" + System.nanoTime(), "评价A店" + System.nanoTime());
        MerchantSession b = merchantSession("rb" + System.nanoTime(), "评价B店" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productA = createProduct(a.token(), categoryId, "评价A品" + System.nanoTime());
        long productB = createProduct(b.token(), categoryId, "评价B品" + System.nanoTime());

        review(customer, completedOrderItem(customer, admin, productA));
        review(customer, completedOrderItem(customer, admin, productB));

        String listA = mockMvc.perform(get("/api/merchant/reviews")
                        .header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(productA))
                .andExpect(jsonPath("$.data.records[0].merchantId").value(a.merchantId()))
                .andReturn().getResponse().getContentAsString();
        long reviewA = objectMapper.readTree(listA).path("data").path("records").get(0).path("id").asLong();

        String listB = mockMvc.perform(get("/api/merchant/reviews")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(productB))
                .andReturn().getResponse().getContentAsString();
        long reviewB = objectMapper.readTree(listB).path("data").path("records").get(0).path("id").asLong();

        // A 不能隐藏 B 店评价
        mockMvc.perform(put("/api/merchant/reviews/" + reviewB + "/visible")
                        .header("Authorization", bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visible": false}
                                """))
                .andExpect(jsonPath("$.code").value(404));

        // A 可以隐藏自己的
        mockMvc.perform(put("/api/merchant/reviews/" + reviewA + "/visible")
                        .header("Authorization", bearer(a.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visible": false}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/products/" + productA + "/reviews"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 平台可按商户筛选，且能看到两条
        mockMvc.perform(get("/api/admin/reviews")
                        .header("Authorization", bearer(admin))
                        .param("merchantId", String.valueOf(a.merchantId())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].merchantName").isNotEmpty());
    }
}
