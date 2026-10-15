package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CartUpdateRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

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
                        .content(objectMapper.writeValueAsString(new CartAddRequest(productId, quantity))))
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
                        .content(objectMapper.writeValueAsString(new CartAddRequest(productId, 4))))
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
}
