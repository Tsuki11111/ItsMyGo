package com.tsuk1.itsmygo;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminMerchantOperationsControllerTest extends ApiTestBase {

    @Test
    void merchantListAndOperationsExposeStats() throws Exception {
        String admin = adminToken("mo" + System.nanoTime());
        String shopName = "经营店" + System.nanoTime();
        MerchantSession session = merchantSession("mo-user-" + System.nanoTime(), shopName);

        mockMvc.perform(get("/api/admin/merchants")
                        .header("Authorization", bearer(admin))
                        .param("keyword", shopName))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].gmv30").value("0.00"))
                .andExpect(jsonPath("$.data.records[0].orders30").value(0))
                .andExpect(jsonPath("$.data.records[0].onSaleProducts").value(0))
                .andExpect(jsonPath("$.data.records[0].lowStockSkus").value(0));

        mockMvc.perform(get("/api/admin/merchants/" + session.merchantId() + "/operations")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.merchant.id").value(session.merchantId()))
                .andExpect(jsonPath("$.data.trend.length()").value(7))
                .andExpect(jsonPath("$.data.topProducts.length()").value(0))
                .andExpect(jsonPath("$.data.recentOrders.length()").value(0));
    }
}
