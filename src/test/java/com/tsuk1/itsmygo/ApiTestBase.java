package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ApiTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    /**
     * 清掉跨用例共享的缓存键。
     * **缓存不参与数据库事务回滚** —— 测试 A 写进缓存的分类树，在它回滚后仍然存在，
     * 会让测试 B 读到「数据库里根本没有」的数据。每个用例开始前清一次最省事。
     */
    @BeforeEach
    void clearSharedCaches() {
        redisTemplate.delete("its-my-go:category:tree");
    }

    /** 走注册接口造一个顾客，返回 token */
    protected String customerToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, "secret123", null))))
                .andReturn().getResponse().getContentAsString();
        return tokenFrom(body);
    }

    /** 注册接口只出顾客，管理员只能直接落库造，再登录取 token */
    protected String adminToken(String username) throws Exception {
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode("secret123"));
        admin.setNickname("admin");
        admin.setPhone("");
        admin.setStatus(1);
        admin.setRole(Roles.ADMIN);
        userMapper.insert(admin);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "secret123"))))
                .andReturn().getResponse().getContentAsString();
        return tokenFrom(body);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    private String tokenFrom(String responseBody) {
        return objectMapper.readTree(responseBody).path("data").path("token").asText();
    }
}
