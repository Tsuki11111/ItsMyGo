package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.AddressRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 商品评价：仅已完成订单项可评、每项一次、管理端可隐藏 */
class ReviewControllerTest extends ApiTestBase {

    private long createAddress(String customer) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "测试收货人", "13800000000", "广东省", "深圳市", "南山区",
                                "科技园 1 号", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 走完 下单 → 支付 → 发货 → 确认收货，返回订单 id */
    private long createCompletedOrder(String customer, String admin) throws Exception {
        long productId = createProductForTest(admin, "评价商品" + System.nanoTime(), "10.00", 5);
        long skuId = defaultSkuId(productId);
        long addressId = createAddress(customer);

        String tokenBody = mockMvc.perform(get("/api/orders/token")
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(tokenBody).path("data").asText();

        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressId": %d, "token": "%s",
                                 "items": [{"productId": %d, "skuId": %d, "quantity": 1}]}
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
                                {"trackingNo": "SF1000000001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/confirm")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        return orderId;
    }

    private long firstOrderItemId(String customer, long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
    }

    private void submitReview(String customer, long orderItemId, int rating, String content) throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": %d, "content": "%s"}
                                """.formatted(orderItemId, rating, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void completedOrderCanBeReviewedAndShowsOnProduct() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long orderId = createCompletedOrder(customer, admin);
        long itemId = firstOrderItemId(customer, orderId);
        long productId = productIdOf(customer, orderId);

        submitReview(customer, itemId, 5, "很好用");

        // 订单详情回填 reviewed
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.items[0].reviewed").value(true));

        // 我的评价
        mockMvc.perform(get("/api/reviews/mine")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].rating").value(5))
                .andExpect(jsonPath("$.data.records[0].status").value(1));

        // 商品公开评价与汇总（匿名可访问）
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].content").value("很好用"))
                .andExpect(jsonPath("$.data.records[0].userNickname").isNotEmpty());

        mockMvc.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(5.0));
    }

    @Test
    void pendingOrderCannotBeReviewed() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long productId = createProductForTest(admin, "未完成商品" + System.nanoTime(), "10.00", 5);
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
                                 "items": [{"productId": %d, "skuId": %d, "quantity": 1}]}
                                """.formatted(addressId, token, productId, skuId)))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("order").path("id").asLong();
        long itemId = firstOrderItemId(customer, orderId);

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": 5, "content": "还没收货"}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有已完成订单可以评价"));
    }

    @Test
    void cannotReviewSameItemTwice() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long orderId = createCompletedOrder(customer, admin);
        long itemId = firstOrderItemId(customer, orderId);

        submitReview(customer, itemId, 5, "第一次");
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": 4, "content": "第二次"}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该商品已评价"));
    }

    @Test
    void ratingMustBeBetweenOneAndFive() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long orderId = createCompletedOrder(customer, admin);
        long itemId = firstOrderItemId(customer, orderId);

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": 6, "content": "越界"}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminCanHideReviewAndItDisappearsFromProduct() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long orderId = createCompletedOrder(customer, admin);
        long itemId = firstOrderItemId(customer, orderId);
        long productId = productIdOf(customer, orderId);

        submitReview(customer, itemId, 3, "一般般");

        String adminList = mockMvc.perform(get("/api/admin/reviews")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn().getResponse().getContentAsString();
        long reviewId = objectMapper.readTree(adminList).path("data").path("records").get(0).path("id").asLong();

        mockMvc.perform(put("/api/admin/reviews/" + reviewId + "/visible")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visible": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.count").value(0));
        mockMvc.perform(get("/api/reviews/mine")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    void customerCannotHideReview() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long orderId = createCompletedOrder(customer, admin);
        long itemId = firstOrderItemId(customer, orderId);
        submitReview(customer, itemId, 5, "正常评价");

        mockMvc.perform(put("/api/admin/reviews/1/visible")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visible": false}
                                """))
                .andExpect(status().isForbidden());
    }

    /** 别人的订单项一律当不存在 */
    @Test
    void cannotReviewOthersOrderItem() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String owner = customerToken("o" + System.nanoTime());
        String other = customerToken("x" + System.nanoTime());
        long orderId = createCompletedOrder(owner, admin);
        long itemId = firstOrderItemId(owner, orderId);

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId": %d, "rating": 5, "content": "冒名"}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void myReviewsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/reviews/mine"))
                .andExpect(status().isUnauthorized());
    }

    private long productIdOf(String customer, long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("items").get(0).path("productId").asLong();
    }
}
