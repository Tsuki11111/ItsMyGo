package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 管理端硬删除：有业务引用必须 409；无引用才允许删。 */
class AdminProductDeleteTest extends ApiTestBase {

    @Autowired
    private ProductMapper productMapper;

    @Test
    void referencedProductCannotBeDeleted() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        long productId = createProductForTest(admin, "DelRef" + System.nanoTime(), "10.00", 5);

        String addrBody = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiver", "QA", "phone", "13800000000", "province", "P",
                                "city", "C", "district", "D", "detail", "D", "isDefault", false))))
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(addrBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", productId, "skuId", defaultSkuId(productId), "quantity", 1))))
                .andExpect(jsonPath("$.code").value(200));

        String tokenBody = mockMvc.perform(get("/api/orders/token").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        String orderToken = objectMapper.readTree(tokenBody).path("data").asText();

        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId, "token", orderToken))))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("order").path("id").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/api/admin/products/" + productId)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(409));
        assertThat(productMapper.selectById(productId)).isNotNull();
    }

    @Test
    void unreferencedProductCanBeDeletedWithSku() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "DelFree" + System.nanoTime(), "10.00", 5);

        mockMvc.perform(delete("/api/admin/products/" + productId)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        assertThat(productMapper.selectById(productId)).isNull();
        assertThat(productSkuMapper.selectCount(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId))).isZero();
    }
}
