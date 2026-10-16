package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.LoginRequest;
import com.tsuk1.itsmygo.dto.RegisterRequest;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
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
    protected ProductSkuMapper productSkuMapper;

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

    /**
     * 商品的**默认 SKU** id（无规格的那条）。
     *
     * 加购和下单都要求传 skuId，而测试通常是先建商品再操作的 ——
     * 与其在每个测试里查一遍，不如统一从这里拿。
     * 拿不到说明商品没铺 SKU（`create` 会自动建，直插库的才会漏），直接炸掉比默默用错 id 好。
     */
    protected long defaultSkuId(long productId) {
        ProductSku sku = productSkuMapper.selectOne(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getSpecValueIds, ""));
        if (sku == null) {
            throw new IllegalStateException("商品 " + productId + " 没有默认 SKU —— 数据没铺开？");
        }
        return sku.getId();
    }

    private String tokenFrom(String responseBody) {
        return objectMapper.readTree(responseBody).path("data").path("token").asText();
    }
}
