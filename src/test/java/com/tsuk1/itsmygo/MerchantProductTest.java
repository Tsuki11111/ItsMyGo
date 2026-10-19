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

/** 商家商品隔离、店铺状态过滤与平台筛选 */
class MerchantProductTest extends ApiTestBase {

    private long createCategory(String admin) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "MC" + System.nanoTime(), 0, true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createMerchantProduct(String token, long categoryId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "商家商品", "", new BigDecimal("19.90"), 8, "", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void merchantOnlySeesAndEditsOwnProducts() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession a = merchantSession("ma" + System.nanoTime(), "A店" + System.nanoTime());
        MerchantSession b = merchantSession("mb" + System.nanoTime(), "B店" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productId = createMerchantProduct(a.token(), categoryId, "A店商品" + System.nanoTime());

        mockMvc.perform(get("/api/merchant/products")
                        .header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].merchantId").value(a.merchantId()));

        mockMvc.perform(get("/api/merchant/products")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.data.total").value(0));

        // B 店看/改 A 店商品一律 404
        mockMvc.perform(get("/api/merchant/products/" + productId)
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(put("/api/merchant/products/" + productId)
                        .header("Authorization", bearer(b.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, "篡改", "", "", new BigDecimal("1.00"), 1, "", false))))
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/merchant/products/" + productId + "/skus")
                        .header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.code").value(404));

        // 平台可以按商户筛选看到它
        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", bearer(admin))
                        .param("merchantId", String.valueOf(a.merchantId())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(productId));
    }

    @Test
    void customerOnlySeesProductsFromActiveMerchants() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("mc" + System.nanoTime(), "C店" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productId = createMerchantProduct(merchant.token(), categoryId, "C店商品" + System.nanoTime());

        mockMvc.perform(get("/api/products")
                        .param("merchantId", String.valueOf(merchant.merchantId())))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/admin/merchants/" + merchant.merchantId() + "/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/products")
                        .param("merchantId", String.valueOf(merchant.merchantId())))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/admin/merchants/" + merchant.merchantId() + "/enable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void adminUpdateDoesNotChangeProductOwner() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MerchantSession merchant = merchantSession("md" + System.nanoTime(), "D店" + System.nanoTime());
        long categoryId = createCategory(admin);
        long productId = createMerchantProduct(merchant.token(), categoryId, "D店商品" + System.nanoTime());

        mockMvc.perform(put("/api/admin/products/" + productId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, "平台改名", "", "", new BigDecimal("29.90"), 8, "", true))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchantId").value(merchant.merchantId()));
    }

    @Test
    void customerCannotUseMerchantProductEndpoints() throws Exception {
        String customer = customerToken("c" + System.nanoTime());

        mockMvc.perform(get("/api/merchant/products")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
