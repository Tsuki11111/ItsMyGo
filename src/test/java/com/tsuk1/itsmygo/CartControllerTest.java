package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CartUpdateRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest extends ApiTestBase {

    long createProduct(String adminToken, String name, String price, int stock) throws Exception {
        String catBody = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(null, "C" + name, 0, true))))
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();

        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "", "", new BigDecimal(price), stock, "", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long addToCart(String token, long productId, int quantity) throws Exception {
        String body = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, defaultSkuId(productId), quantity))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("items").get(0).path("id").asLong();
    }

    @Test
    void addSameProductTwiceAccumulatesQuantity() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "P1", "10.00", 100);

        addToCart(token, productId, 2);
        addToCart(token, productId, 3);

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.items[0].subtotal").isNumber());
    }

    @Test
    void selectedTotalOnlyCountsSelectedAndAvailable() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long cheap = createProduct(admin, "Cheap", "10.00", 100);
        long pricey = createProduct(admin, "Pricey", "20.50", 100);

        addToCart(token, cheap, 2);
        long priceyItem = addToCart(token, pricey, 1);

        // 初始都勾选：10*2 + 20.50 = 40.50
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.selectedTotal").value(40.50));

        // 取消勾选贵的那件
        mockMvc.perform(put("/api/cart/items/" + priceyItem)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartUpdateRequest(1, false))))
                .andExpect(jsonPath("$.data.selectedTotal").value(20.00));
    }

    @Test
    void cannotExceedStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Scarce", "10.00", 3);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, defaultSkuId(productId), 4))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("库存不足"));
    }

    @Test
    void otherUserCannotTouchSomeoneElsesCart() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String alice = customerToken("a" + System.nanoTime());
        String bob = customerToken("b" + System.nanoTime());
        long productId = createProduct(admin, "Shared", "10.00", 100);

        long aliceItem = addToCart(alice, productId, 1);

        // Bob 看不到 Alice 的购物车
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // 也改不了、删不掉
        mockMvc.perform(put("/api/cart/items/" + aliceItem)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartUpdateRequest(99, true))))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/cart/items/" + aliceItem).header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.code").value(404));

        // Alice 的购物车纹丝不动
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1));
    }

    @Test
    void removeItemWorks() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "Temp", "10.00", 100);
        long itemId = addToCart(token, productId, 1);

        mockMvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void cartRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cartCountRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/cart/count"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 角标只服务 30 秒轮询，是购物车最热的读路径：
     * 第一次读建缓存，之后的写操作必须把它删掉，否则角标会停在旧值。
     */
    @Test
    void cartCountIsCachedAndInvalidatedOnWrites() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "c" + System.nanoTime();
        String token = customerToken(username);
        String key = "its-my-go:cart:count:" + userIdOf(username);
        long productId = createProduct(admin, "Counted", "10.00", 100);

        // 空购物车也缓存（count=0），否则每个新用户每轮轮询都会回源
        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.count").value(0));
        assertThat(redisTemplate.opsForHash().get(key, "count")).isEqualTo("0");
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(0);

        // 读命中不续期：轮询 30 秒一次，若每次读都续期，活跃用户的角标会永远停在旧值
        redisTemplate.expire(key, 100, TimeUnit.SECONDS);
        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.count").value(0));
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isLessThanOrEqualTo(100);

        long itemId = addToCart(token, productId, 2);
        // 写库后立刻删缓存
        assertThat(redisTemplate.hasKey(key)).isFalse();

        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.count").value(2));
        assertThat(redisTemplate.opsForHash().get(key, "count")).isEqualTo("2");
        assertThat(redisTemplate.opsForHash().get(key, "sku:" + defaultSkuId(productId)))
                .isEqualTo("2");

        // 改数量同样失效，下一次读重建
        mockMvc.perform(put("/api/cart/items/" + itemId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartUpdateRequest(3, true))))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.count").value(3));

        mockMvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void corruptCartCountCacheIsRebuiltInsteadOfFailing() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "c" + System.nanoTime();
        String token = customerToken(username);
        String key = "its-my-go:cart:count:" + userIdOf(username);
        long productId = createProduct(admin, "BrokenCache", "10.00", 100);
        addToCart(token, productId, 4);

        // 模拟格式变更 / 人工改坏：读取时必须删掉重建，而不是把异常抛给用户
        redisTemplate.opsForHash().put(key, "count", "not-a-number");

        mockMvc.perform(get("/api/cart/count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(4));
        assertThat(redisTemplate.opsForHash().get(key, "count")).isEqualTo("4");
    }

    private long userIdOf(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }
}
