package com.tsuk1.itsmygo;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 问大家：公开可读，任何登录用户可提问，未购买用户不能回答。 */
class ProductQaControllerTest extends ApiTestBase {

    @Test
    void anyoneCanAskButOnlyBuyersCanAnswer() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "问答测试商品", "10.00", 5);
        String customer = customerToken("qa" + System.nanoTime());

        mockMvc.perform(post("/api/products/" + productId + "/questions")
                        .header("Authorization", bearer(customer))
                        .contentType("application/json")
                        .content("""
                                {"content":"这个支持壁挂吗？"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        org.springframework.test.web.servlet.MvcResult listResult =
                mockMvc.perform(get("/api/products/" + productId + "/questions"))
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.records[0].content").value("这个支持壁挂吗？"))
                        .andReturn();
        long questionId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records").get(0).path("id").asLong();

        // 没买过的人可以提问，但不能回答
        mockMvc.perform(post("/api/products/" + productId + "/questions/" + questionId + "/answers")
                        .header("Authorization", bearer(customer))
                        .contentType("application/json")
                        .content("""
                                {"content":"我猜可以"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }
}
