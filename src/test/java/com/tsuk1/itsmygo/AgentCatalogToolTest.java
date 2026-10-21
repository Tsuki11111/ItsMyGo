package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** agent 商品目录：筛选、排序、评分/库存字段与语义索引全量接口。 */
class AgentCatalogToolTest extends ApiTestBase {

    @Autowired
    private AgentToolTicketService ticketService;

    @Autowired
    private ProductMapper productMapper;

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    @Test
    void searchSupportsFiltersSortAndGuideFields() throws Exception {
        String username = "cat" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        String admin = adminToken("adm" + System.nanoTime());
        String keyword = "st" + System.nanoTime();
        long productA = createProductForTest(admin, keyword + "A", "10.00", 5);
        long productB = createProductForTest(admin, keyword + "B", "100.00", 6);
        long skuB = defaultSkuId(productB);
        Long categoryA = productMapper.selectById(productA).getCategoryId();
        String ticket = ticketService.issue(uid, "sess-catalog");

        // 价格区间
        mockMvc.perform(post("/internal/agent/catalog/products")
                        .header("X-Agent-Ticket", ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"%s","priceMin":50}
                                """.formatted(keyword)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(productB))
                .andExpect(jsonPath("$.data[0].price").value("100.00"))
                .andExpect(jsonPath("$.data[0].totalStock").value(6))
                .andExpect(jsonPath("$.data[0].sales").value(0))
                .andExpect(jsonPath("$.data[0].rating").value(""))
                .andExpect(jsonPath("$.data[0].merchantName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].categoryName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].skus[0].skuId").value(skuB));

        // 价格升序
        mockMvc.perform(post("/internal/agent/catalog/products")
                        .header("X-Agent-Ticket", ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"%s","sort":"price_asc"}
                                """.formatted(keyword)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value(productA))
                .andExpect(jsonPath("$.data[1].productId").value(productB));

        // 分类过滤
        mockMvc.perform(post("/internal/agent/catalog/products")
                        .header("X-Agent-Ticket", ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"%s","categoryId":%d}
                                """.formatted(keyword, categoryA)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(productA));

        // 语义索引全量
        mockMvc.perform(get("/internal/agent/catalog/all")
                        .header("X-Agent-Ticket", ticket)
                        .param("limit", "100"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(content().string(containsString(keyword)));

        // 分类树
        mockMvc.perform(get("/internal/agent/catalog/categories")
                        .header("X-Agent-Ticket", ticket))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").exists());
    }

    @Test
    void catalogEndpointsRequireTicket() throws Exception {
        mockMvc.perform(get("/internal/agent/catalog/all"))
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/internal/agent/catalog/categories"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
