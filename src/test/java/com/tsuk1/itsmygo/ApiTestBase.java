package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.UserMapper;
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
     * 清掉跨用例共享的状态。
     *
     * - **缓存与限流计数都不参与数据库事务回滚** —— 测试 A 留下的分类树缓存，
     *   或者打满的注册计数，都会让测试 B 莫名其妙地失败。
     * - 放在造数据的方法里而不是只靠 `@BeforeEach`：**每次真正要打接口前都清一次**，
     *   比依赖生命周期回调的时机更可靠（`@TestPropertySource` 在抽象父类上就没能生效）。
     */
    protected void resetRateLimits() {
        var rateKeys = redisTemplate.keys("its-my-go:rate:*");
        if (rateKeys != null && !rateKeys.isEmpty()) {
            redisTemplate.delete(rateKeys);
        }
    }

    /** 走注册接口造一个顾客，返回 token */
    protected String customerToken(String username) throws Exception {
        resetRateLimits();
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, "secret123", null))))
                .andReturn().getResponse().getContentAsString();
        return tokenFrom(body);
    }

    /** 注册接口只出顾客，管理员只能直接落库造，再登录取 token */
    protected String adminToken(String username) throws Exception {
        resetRateLimits();
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
