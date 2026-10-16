package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口限流。
 *
 * 限流计数是全局共享状态，用例之间会互相踩（都来自同一个 IP），
 * 所以每个测试方法的开头都会先 {@code resetRateLimits()} 清一次再开始打配额。
 */
class RateLimitTest extends ApiTestBase {

    private static final String REGISTER_URL = "/api/auth/register";

    @Test
    void registerIsLimitedByIp() throws Exception {
        resetRateLimits();   // 前面的测试类可能留下计数，从干净状态开始
        // 配额是 5 次/分钟，第 6 次必须被拒
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RegisterRequest("rl" + System.nanoTime() + i, "secret123", null))))
                    .andExpect(jsonPath("$.code").value(200));
        }

        // 注意：HTTP 状态仍是 200，被拒体现在 body 的 code 上
        //（401/403 才是真 HTTP 状态码 —— 那是 Spring Security 的 entry point 手动 setStatus 的）
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("rl" + System.nanoTime() + "x", "secret123", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));
    }

    /** 限流是按场景隔离的：注册打满了，不影响登录 */
    @Test
    void limitsAreIsolatedPerScene() throws Exception {
        resetRateLimits();
        String username = "iso" + System.nanoTime();
        // 凑满 5 次注册（1 + 4）
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, "secret123", null))))
                .andExpect(jsonPath("$.code").value(200));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RegisterRequest("iso" + System.nanoTime() + i, "secret123", null))))
                    .andExpect(jsonPath("$.code").value(200));
        }
        // 第 6 次已打满
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("iso" + System.nanoTime() + "z", "secret123", null))))
                .andExpect(jsonPath("$.code").value(429));

        // 登录是另一个场景，照样能过
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "secret123"))))
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 下单按用户限流：配额 10 次/分钟 */
    @Test
    void placingOrdersIsLimitedPerUser() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("rl" + System.nanoTime());
        long productId = createProduct(admin);
        addToCart(token, productId);

        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < 12; i++) {
            var response = mockMvc.perform(post("/api/orders")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new com.tsuk1.itsmygo.dto.CreateOrderRequest(
                                    addressIdOf(token), tokenOf(token), null))))
                    .andReturn().getResponse().getContentAsString();
            int code = objectMapper.readTree(response).path("code").asInt();
            if (code == 429) {
                rejected++;
            } else {
                allowed++;
            }
        }

        // 前面的请求可能因「购物车为空」等原因失败，但绝不该出现 429 之外的限流漏放
        org.assertj.core.api.Assertions.assertThat(allowed).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(rejected).isEqualTo(2);
    }

    // ── 下面是给上面那个测试用的小工具 ──

    private long createProduct(String adminToken) throws Exception {
        String catBody = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "RL" + System.nanoTime(), 0, true))))
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();

        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, "RLP" + System.nanoTime(), "", "",
                                new BigDecimal("10.00"), 100, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void addToCart(String token, long productId) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CartAddRequest(productId, 1))));
    }

    private long addressIdOf(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressRequest(
                                "R", "13800000000", "P", "C", "D", "Detail", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String tokenOf(String userToken) throws Exception {
        String body = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(userToken)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }
}
