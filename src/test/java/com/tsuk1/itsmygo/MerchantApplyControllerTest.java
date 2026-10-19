package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.MerchantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 商家入驻、平台审核、禁用与权限隔离 */
class MerchantApplyControllerTest extends ApiTestBase {

    private String merchantJson(String name) throws Exception {
        return objectMapper.writeValueAsString(new MerchantRequest(
                name, "", "测试店铺", "店主", "13800000000"));
    }

    private long apply(String customer, String name) throws Exception {
        String body = mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void approve(String admin, long merchantId) throws Exception {
        mockMvc.perform(post("/api/admin/merchants/" + merchantId + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void customerApplyThenAdminApproveGrantsMerchantAccess() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "m" + System.nanoTime();
        String customer = customerToken(username);
        String shopName = "测试店铺" + System.nanoTime();
        long merchantId = apply(customer, shopName);

        // 审核前：顾客不能进商家后台，公开店铺也不可见
        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shops/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/admin/merchants")
                        .header("Authorization", bearer(admin))
                        .param("status", "PENDING"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ownerUsername").value(username));

        approve(admin, merchantId);

        // 同一个 token：授权以数据库为准，立即可用
        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(merchantId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/shops/" + merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(shopName));

        mockMvc.perform(get("/api/merchant-apply")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rejectedApplicationCanBeResubmitted() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("m" + System.nanoTime());
        long merchantId = apply(customer, "驳回测试" + System.nanoTime());

        mockMvc.perform(post("/api/admin/merchants/" + merchantId + "/reject")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "资料不完整"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/merchant-apply")
                        .header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("资料不完整"));

        // 重新提交复用原申请，不新增一条
        mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("驳回后重提" + System.nanoTime())))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(merchantId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/admin/merchants")
                        .header("Authorization", bearer(admin))
                        .param("status", "PENDING"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void pendingApplicationCannotBeAppliedTwice() throws Exception {
        String customer = customerToken("m" + System.nanoTime());
        apply(customer, "重复申请" + System.nanoTime());

        mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("重复申请二" + System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("入驻申请审核中"));
    }

    @Test
    void approvedMerchantCannotApplyAgain() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("m" + System.nanoTime());
        long merchantId = apply(customer, "已入驻" + System.nanoTime());
        approve(admin, merchantId);

        mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("再申请" + System.nanoTime())))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该账号已是商家"));
    }

    @Test
    void disabledMerchantLosesAccessAndShopDisappears() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("m" + System.nanoTime());
        long merchantId = apply(customer, "禁用测试" + System.nanoTime());
        approve(admin, merchantId);

        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/merchants/" + merchantId + "/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        // 旧 token 立即失去商家权限
        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shops/" + merchantId))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/admin/merchants/" + merchantId + "/enable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/merchant/profile")
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
    }

    @Test
    void defaultMerchantCannotBeReviewedOrDisabled() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(post("/api/admin/merchants/1/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("平台自营店铺不能审核"));

        mockMvc.perform(post("/api/admin/merchants/1/disable")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("平台自营店铺不能禁用"));
    }

    @Test
    void shopNameMustBeUnique() throws Exception {
        String first = customerToken("a" + System.nanoTime());
        String second = customerToken("b" + System.nanoTime());
        String name = "同名店" + System.nanoTime();
        apply(first, name);

        mockMvc.perform(post("/api/merchant-apply")
                        .header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson(name)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("店铺名已被占用"));
    }

    @Test
    void merchantCanUpdateOwnProfile() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("m" + System.nanoTime());
        long merchantId = apply(customer, "资料修改" + System.nanoTime());
        approve(admin, merchantId);

        mockMvc.perform(put("/api/merchant/profile")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MerchantRequest(
                                "新店名" + System.nanoTime(), "logo.png", "新简介",
                                "新联系人", "13900000000"))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.logo").value("logo.png"))
                .andExpect(jsonPath("$.data.description").value("新简介"));
    }

    @Test
    void adminListIncludesPlatformMerchantWithoutOwner() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());

        mockMvc.perform(get("/api/admin/merchants")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].ownerUsername").value(""));
    }

    @Test
    void merchantApplyRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/merchant-apply"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/merchant-apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merchantJson("未登录" + System.nanoTime())))
                .andExpect(status().isUnauthorized());
    }
}
