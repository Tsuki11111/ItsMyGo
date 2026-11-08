package com.tsuk1.itsmygo;

import agent.v1.Event;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductPriceHistoryMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能推广：写进「客服智能体会话」（内部事件 promotion-push），每天最多 1 条；
 * 偏好关闭不发；批量路径只处理开启推广的启用用户。
 */
class PromotionServiceTest extends ApiTestBase {

    private static final String EVENT = "promotion-push";

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private ProductPriceHistoryMapper historyMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserMapper promotionUserMapper;

    @Autowired
    private AgentPreferenceMapper preferenceMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @MockitoBean
    private AgentGateway agentGateway;

    @BeforeEach
    void stubAgent() {
        when(agentGateway.configured()).thenReturn(true);
        when(agentGateway.internalEvent(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(Collections.<Event>emptyIterator());
    }

    private long createUser() {
        User user = new User();
        String stamp = String.valueOf(System.nanoTime());
        user.setUsername("promo" + stamp);
        user.setPassword("x");
        user.setNickname("promo" + stamp);
        user.setPhone("");
        user.setAvatar("");
        user.setStatus(1);
        user.setRole(Roles.CUSTOMER);
        promotionUserMapper.insert(user);
        return user.getId();
    }

    private AgentPreference setPreference(long userId, boolean enabled) {
        preferenceMapper.upsert(userId, enabled, true, false);
        return preferenceMapper.selectById(userId);
    }

    /** 造一个一定能赢的商品：30 天前 100 元、现价 10 元、销量高、评分 5 分 */
    private long createPromotionProduct() {
        Category category = new Category();
        category.setParentId(0L);
        category.setName("Promo" + System.nanoTime());
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);

        Product product = new Product();
        product.setMerchantId(Merchant.PLATFORM_MERCHANT_ID);
        product.setCategoryId(category.getId());
        product.setName("Promo " + System.nanoTime());
        product.setBrand("PromoBrand");
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(100);
        product.setSales(1000);
        product.setStatus(1);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSpecValueIds("");
        sku.setSpecText("");
        sku.setPrice(product.getPrice());
        sku.setStock(product.getStock());
        sku.setSales(product.getSales());
        sku.setImage("");
        sku.setStatus(1);
        productSkuMapper.insert(sku);

        ProductPriceHistory peak = new ProductPriceHistory();
        peak.setProductId(product.getId());
        peak.setSkuId(null);
        peak.setPrice(new BigDecimal("100.00"));
        peak.setReason(ProductPriceHistory.REASON_BASELINE);
        peak.setChangedAt(LocalDateTime.now().minusDays(10));
        historyMapper.insert(peak);

        Review review = new Review();
        review.setUserId(1L);
        review.setOrderId(1L);
        review.setOrderItemId(System.nanoTime());
        review.setProductId(product.getId());
        review.setSkuId(sku.getId());
        review.setRating(5);
        review.setContent("好评");
        review.setStatus(1);
        reviewMapper.insert(review);
        return product.getId();
    }

    private String capturePromotionPayload(int expectedCalls) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(agentGateway, times(expectedCalls)).internalEvent(anyLong(), anyString(),
                eq(EVENT), payload.capture(), anyString(), any());
        return payload.getValue();
    }

    @Test
    void sendsOnePromotionIntoAgentSessionPerDay() {
        long userId = createUser();
        AgentPreference preference = setPreference(userId, true);
        assertThat(preference.getLastPromotionDate()).isNull();
        long productA = createPromotionProduct();
        long productB = createPromotionProduct();
        long productC = createPromotionProduct();

        assertThat(promotionService.notifyForUser(userId)).isTrue();

        String payload = capturePromotionPayload(1);
        assertThat(payload).contains("\"productId\":\"" + productA + "\"")
                .contains("\"productId\":\"" + productB + "\"")
                .contains("\"productId\":\"" + productC + "\"")
                .contains("近 30 天降了约 90%")
                .contains("已售 1000 件")
                .contains("评分 5.0 分")
                .contains("最近有几件价格挺合适")
                .contains("近 30 天降了约 90%")
                .contains("①")
                .contains("②")
                .contains("点商品卡看详情")
                .contains("我帮您下单");
        // 不再每行重复同一句 CTA
        assertThat(payload).doesNotContain("要不要看看？");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertThat(preferenceMapper.selectById(userId).getLastPromotionDate()).isEqualTo(today);

        // 同一天再调：不再发、也不会再调 agent
        assertThat(promotionService.notifyForUser(userId)).isFalse();
        verify(agentGateway, times(1)).internalEvent(anyLong(), anyString(), eq(EVENT),
                anyString(), anyString(), any());
    }

    @Test
    void disabledPreferenceSendsNothing() {
        long userId = createUser();
        setPreference(userId, false);
        createPromotionProduct();

        assertThat(promotionService.notifyForUser(userId)).isFalse();

        verify(agentGateway, never()).internalEvent(anyLong(), anyString(), eq(EVENT),
                anyString(), anyString(), any());
        assertThat(preferenceMapper.selectById(userId).getLastPromotionDate()).isNull();
    }

    @Test
    void runDailyOnlySendsToEnabledUsers() {
        long enabledA = createUser();
        long enabledB = createUser();
        long disabled = createUser();
        setPreference(enabledA, true);
        setPreference(enabledB, true);
        setPreference(disabled, false);
        createPromotionProduct();

        int sent = promotionService.runDaily();

        assertThat(sent).isGreaterThanOrEqualTo(2);
        assertThat(preferenceMapper.selectById(enabledA).getLastPromotionDate()).isNotNull();
        assertThat(preferenceMapper.selectById(enabledB).getLastPromotionDate()).isNotNull();
        assertThat(preferenceMapper.selectById(disabled).getLastPromotionDate()).isNull();
    }

    @Test
    void agentUnavailableDoesNotMarkSent() {
        when(agentGateway.configured()).thenReturn(false);
        long userId = createUser();
        setPreference(userId, true);
        createPromotionProduct();

        assertThat(promotionService.notifyForUser(userId)).isFalse();
        assertThat(preferenceMapper.selectById(userId).getLastPromotionDate()).isNull();
    }
}
