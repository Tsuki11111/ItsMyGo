package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.dto.AddressRequest;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AddressControllerTest extends ApiTestBase {

    private String tokenFor(String username) throws Exception {
        resetRateLimits();   // 注册接口有 IP 限流，用例之间会累积
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, "secret123", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private AddressRequest req(String receiver, boolean isDefault) {
        return new AddressRequest(receiver, "13800000000", "Guangdong", "Shenzhen", "Nanshan", "Keyuan 1", isDefault);
    }

    private long createAddress(String token, AddressRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void ownerCanCrudOwnAddress() throws Exception {
        String token = tokenFor("a" + System.nanoTime());
        long id = createAddress(token, req("Alice", false));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].receiver").value("Alice"));

        mockMvc.perform(put("/api/addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("Alice2", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiver").value("Alice2"));

        mockMvc.perform(delete("/api/addresses/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** M1 的核心验收：别人的地址不能读、不能改、不能删，且一律返回 404 而非 403 */
    @Test
    void otherUserCannotTouchSomeoneElsesAddress() throws Exception {
        String alice = tokenFor("a" + System.nanoTime());
        String bob = tokenFor("b" + System.nanoTime());
        long aliceAddressId = createAddress(alice, req("Alice", false));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put("/api/addresses/" + aliceAddressId)
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("Hacked", false))))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/addresses/" + aliceAddressId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.code").value(404));

        // Alice 的地址必须纹丝不动
        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].receiver").value("Alice"));
    }

    @Test
    void settingNewDefaultClearsPreviousDefault() throws Exception {
        String token = tokenFor("d" + System.nanoTime());
        long first = createAddress(token, req("First", true));
        createAddress(token, req("Second", true));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].receiver").value("Second"))
                .andExpect(jsonPath("$.data[0].isDefault").value(1))
                .andExpect(jsonPath("$.data[1].id").value(first))
                .andExpect(jsonPath("$.data[1].isDefault").value(0));
    }

    /** 独立的「设默认」接口：只改这一行，不要求客户端回传全部字段 */
    @Test
    void setDefaultEndpointMovesTheFlag() throws Exception {
        String token = tokenFor("e" + System.nanoTime());
        long first = createAddress(token, req("First", true));
        long second = createAddress(token, req("Second", false));

        mockMvc.perform(put("/api/addresses/" + second + "/default")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].isDefault").value(1))
                .andExpect(jsonPath("$.data[1].id").value(first))
                .andExpect(jsonPath("$.data[1].isDefault").value(0));

        // 再设一次应当幂等
        mockMvc.perform(put("/api/addresses/" + second + "/default")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void cannotSetSomeoneElsesAddressAsDefault() throws Exception {
        String alice = tokenFor("a" + System.nanoTime());
        String bob = tokenFor("b" + System.nanoTime());
        long aliceAddressId = createAddress(alice, req("Alice", false));

        mockMvc.perform(put("/api/addresses/" + aliceAddressId + "/default")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data[0].isDefault").value(0));
    }

    /** 删掉默认地址后**不自动递补** —— 没有可靠的「下一条」，静默提升更让人意外 */
    @Test
    void deletingTheDefaultLeavesNoDefault() throws Exception {
        String token = tokenFor("f" + System.nanoTime());
        long first = createAddress(token, req("First", true));
        createAddress(token, req("Second", false));

        mockMvc.perform(delete("/api/addresses/" + first).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].isDefault").value(0));
    }

    @Test
    void addressEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
