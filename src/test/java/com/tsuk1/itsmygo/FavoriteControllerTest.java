package com.tsuk1.itsmygo;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 顾客端收藏：幂等、列表、状态、越权与未登录 */
class FavoriteControllerTest extends ApiTestBase {

    @Test
    void favoriteLifecycleIsIdempotent() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long productId = createProductForTest(admin, "收藏商品", "10.00", 5);

        mockMvc.perform(get("/api/favorites/" + productId + "/status")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/api/favorites/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 再收藏一次仍然成功，且列表里只有一条
        mockMvc.perform(post("/api/favorites/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(productId))
                .andExpect(jsonPath("$.data.records[0].name").value("收藏商品"))
                .andExpect(jsonPath("$.data.records[0].status").value(1));

        mockMvc.perform(get("/api/favorites/" + productId + "/status")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(delete("/api/favorites/" + productId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/favorites/" + productId + "/status")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data").value(false));
    }

    /** 别人的收藏互不可见 */
    @Test
    void favoritesAreScopedToOwner() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String a = customerToken("a" + System.nanoTime());
        String b = customerToken("b" + System.nanoTime());
        long productId = createProductForTest(admin, "隔离收藏", "10.00", 5);

        mockMvc.perform(post("/api/favorites/" + productId)
                        .header("Authorization", bearer(a)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", bearer(b)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void favoriteRejectsMissingProduct() throws Exception {
        String customer = customerToken("c" + System.nanoTime());

        mockMvc.perform(post("/api/favorites/999999999")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    void favoriteRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isUnauthorized());
    }
}
