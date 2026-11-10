package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.entity.PlatformPolicy;
import com.tsuk1.itsmygo.entity.PlatformPolicySection;
import com.tsuk1.itsmygo.mapper.PlatformPolicyMapper;
import com.tsuk1.itsmygo.mapper.PlatformPolicySectionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformPolicyControllerTest extends ApiTestBase {

    @Autowired
    private PlatformPolicyMapper policyMapper;

    @Autowired
    private PlatformPolicySectionMapper sectionMapper;

    @Test
    void listsActivePoliciesAndReturnsSectionsInOrder() throws Exception {
        PlatformPolicy policy = new PlatformPolicy();
        policy.setPolicyKey("refund");
        policy.setTitle("退换货与售后政策");
        policy.setCategory("售后与退款");
        policy.setVersion("1.0");
        policy.setStatus("ACTIVE");
        policy.setContentMd("# 退换货与售后政策\n\n## 一、退货\n正文\n");
        policyMapper.insert(policy);

        PlatformPolicySection second = section(policy.getId(), 2, "二、退款", "退款正文");
        PlatformPolicySection first = section(policy.getId(), 1, "一、退货", "退货正文");
        sectionMapper.insert(second);
        sectionMapper.insert(first);

        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].policyKey").value("refund"))
                .andExpect(jsonPath("$.data[0].sectionCount").value(2));

        mockMvc.perform(get("/api/policies/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("退换货与售后政策"))
                .andExpect(jsonPath("$.data.sections[0].sectionNo").value(1))
                .andExpect(jsonPath("$.data.sections[0].heading").value("一、退货"))
                .andExpect(jsonPath("$.data.sections[0].content").value("退货正文"))
                .andExpect(jsonPath("$.data.sections[1].sectionNo").value(2));
    }

    private PlatformPolicySection section(long policyId, int no, String heading, String content) {
        PlatformPolicySection section = new PlatformPolicySection();
        section.setPolicyId(policyId);
        section.setSectionNo(no);
        section.setHeading(heading);
        section.setContent(content);
        return section;
    }
}
