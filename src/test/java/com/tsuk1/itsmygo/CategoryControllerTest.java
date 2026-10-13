package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest extends ApiTestBase {

    private long createCategory(String adminToken, Long parentId, String name, boolean enabled) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(parentId, name, 0, enabled))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void adminBuildsTreeAndCustomerSeesEnabledOnes() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long root = createCategory(admin, null, "Printers", true);
        createCategory(admin, root, "Laser", true);
        createCategory(admin, root, "Inkjet", true);

        // 管理端：树结构带 children
        mockMvc.perform(get("/api/admin/categories").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(root))
                .andExpect(jsonPath("$.data[0].children.length()").value(2));

        // 顾客端：匿名可访问，同样是树
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Printers"))
                .andExpect(jsonPath("$.data[0].children.length()").value(2));
    }

    @Test
    void disabledCategoryIsHiddenFromCustomersButVisibleToAdmin() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        createCategory(admin, null, "Hidden", false);

        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/admin/categories").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void nonAdminCannotTouchCategoryManagement() throws Exception {
        String customer = customerToken("cust" + System.nanoTime());

        mockMvc.perform(get("/api/admin/categories").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(null, "X", 0, true))))
                .andExpect(status().isForbidden());

        // 匿名同样进不去
        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotDeleteCategoryThatHasChildren() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long root = createCategory(admin, null, "Root", true);
        createCategory(admin, root, "Child", true);

        mockMvc.perform(delete("/api/admin/categories/" + root).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该分类下还有子分类，请先处理"));
    }

    @Test
    void cannotMakeCategoryItsOwnParent() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long id = createCategory(admin, null, "Self", true);

        mockMvc.perform(put("/api/admin/categories/" + id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(id, "Self", 0, true))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能把自己设为父分类"));
    }
}
