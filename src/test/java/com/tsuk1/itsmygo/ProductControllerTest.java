package com.tsuk1.itsmygo;

import tools.jackson.databind.JsonNode;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest extends ApiTestBase {

    @Autowired
    private ProductMapper productMapper;

    private long createCategory(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(null, name, 0, true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createProduct(String adminToken, long categoryId, String name, String subtitle,
                               String price, boolean onSale) throws Exception {
        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, subtitle, "", new BigDecimal(price), 10, "detail", onSale))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void adminCanFilterLowStockAndBatchStatus() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Ops");
        long productId = createProduct(admin, categoryId, "LowStockItem", "", "10.00", true);

        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", bearer(admin))
                        .param("lowStock", "true")
                        .param("keyword", "LowStockItem"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(productId));

        mockMvc.perform(put("/api/admin/products/batch-status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + productId + "],\"onSale\":false}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", bearer(admin))
                        .param("keyword", "LowStockItem"))
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    void adminPublishesProductAndCustomerFindsIt() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        createProduct(admin, categoryId, "HAK 180", "hot stamping machine", "1999.00", true);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("HAK 180"))
                .andExpect(jsonPath("$.data.records[0].price").isNumber())
                .andExpect(jsonPath("$.data.records[0].stock").value(10));
    }

    @Test
    void offShelfProductIsHiddenFromCustomer() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        long hiddenId = createProduct(admin, categoryId, "Hidden", "", "10.00", false);
        createProduct(admin, categoryId, "Visible", "", "20.00", true);

        // 顾客端列表只剩上架的那件
        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("Visible"));

        // 顾客端详情按下架处理，返回 404
        mockMvc.perform(get("/api/products/" + hiddenId))
                .andExpect(jsonPath("$.code").value(404));

        // 管理端两件都能看到
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void keywordSearchMatchesNameAndSubtitle() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        createProduct(admin, categoryId, "HAK 180", "", "1999.00", true);
        createProduct(admin, categoryId, "LaserJet", "office printer", "899.00", true);

        mockMvc.perform(get("/api/products").param("keyword", "HAK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("HAK 180"));

        // 副标题也参与匹配
        mockMvc.perform(get("/api/products").param("keyword", "office"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("LaserJet"));

        mockMvc.perform(get("/api/products").param("keyword", "nothing-matches"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void brandZoneAggregatesAndFiltersProducts() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        long pantumId = createProduct(admin, categoryId, "Pantum A", "", "10.00", true);
        long lenovoId = createProduct(admin, categoryId, "Lenovo A", "", "20.00", true);
        setBrand(pantumId, "奔图");
        setBrand(lenovoId, "联想");

        // 品牌筛选是精确匹配，不再依赖商品名里出现品牌词
        mockMvc.perform(get("/api/products").param("brand", "奔图"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(pantumId));

        String body = mockMvc.perform(get("/api/products/brands"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode brands = objectMapper.readTree(body).path("data");
        assertThat(brands).hasSize(2);
        assertThat(brands).extracting(node -> node.path("name").asText())
                .containsExactlyInAnyOrder("奔图", "联想");
        assertThat(brands).allMatch(node -> node.path("count").asLong() == 1L);
    }

    private void setBrand(long productId, String brand) {
        Product patch = new Product();
        patch.setId(productId);
        patch.setBrand(brand);
        productMapper.updateById(patch);
    }

    @Test
    void categoryFilterAndPagingWork() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long printers = createCategory(admin, "Printers");
        long routers = createCategory(admin, "Routers");
        createProduct(admin, printers, "P1", "", "1.00", true);
        createProduct(admin, printers, "P2", "", "2.00", true);
        createProduct(admin, printers, "P3", "", "3.00", true);
        createProduct(admin, routers, "R1", "", "4.00", true);

        mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(printers)))
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/products").param("size", "2"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void nonAdminCannotManageProducts() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        String customer = customerToken("cust" + System.nanoTime());

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, "X", "", "", new BigDecimal("1.00"), 1, "", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeleteCategoryThatStillHasProducts() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long categoryId = createCategory(admin, "Printers");
        createProduct(admin, categoryId, "P1", "", "1.00", true);

        mockMvc.perform(delete("/api/admin/categories/" + categoryId).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该分类下还有商品，请先处理"));
    }
}
