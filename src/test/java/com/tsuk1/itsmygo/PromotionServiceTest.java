package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.ChatMessage;
import com.tsuk1.itsmygo.entity.Conversation;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ChatMessageMapper;
import com.tsuk1.itsmygo.mapper.ConversationMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductPriceHistoryMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.PromotionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 智能推广：每天最多 1 条、偏好关闭不发、批量路径只处理开启推广的用户。 */
class PromotionServiceTest extends ApiTestBase {

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

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

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

    /** 造一个一定能赢的商品：30 天前 100 元、现价 10 元，销量高、评分 5 分 */
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

    private List<ChatMessage> promotionMessages(long userId) {
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getCustomerUserId, userId)
                .eq(Conversation::getMerchantId, Merchant.PLATFORM_MERCHANT_ID));
        if (conversation == null) {
            return List.of();
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getContentType, ChatMessage.TYPE_PROMOTION));
    }

    @Test
    void sendsOnePromotionPerDayAndMarksPreference() {
        long userId = createUser();
        AgentPreference preference = setPreference(userId, true);
        assertThat(preference.getLastPromotionDate()).isNull();
        long productId = createPromotionProduct();

        assertThat(promotionService.notifyForUser(userId)).isTrue();

        List<ChatMessage> messages = promotionMessages(userId);
        assertThat(messages).hasSize(1);
        ChatMessage message = messages.get(0);
        assertThat(message.getSenderType()).isEqualTo(ChatMessage.SENDER_MERCHANT);
        assertThat(message.getCardData()).contains("\"productId\":" + productId);
        assertThat(message.getContent()).contains("最近 30 天降价约 90%")
                .contains("已售 1000 件")
                .contains("评分 5.0 分");

        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getCustomerUserId, userId)
                .eq(Conversation::getMerchantId, Merchant.PLATFORM_MERCHANT_ID));
        assertThat(conversation.getCustomerUnread()).isEqualTo(1);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertThat(preferenceMapper.selectById(userId).getLastPromotionDate()).isEqualTo(today);

        // 同一天再调：不再发
        assertThat(promotionService.notifyForUser(userId)).isFalse();
        assertThat(promotionMessages(userId)).hasSize(1);
    }

    @Test
    void disabledPreferenceSendsNothing() {
        long userId = createUser();
        setPreference(userId, false);
        createPromotionProduct();

        assertThat(promotionService.notifyForUser(userId)).isFalse();
        assertThat(promotionMessages(userId)).isEmpty();
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
        assertThat(promotionMessages(enabledA)).hasSize(1);
        assertThat(promotionMessages(enabledB)).hasSize(1);
        assertThat(promotionMessages(disabled)).isEmpty();
        assertThat(preferenceMapper.selectById(disabled).getLastPromotionDate()).isNull();
    }
}
