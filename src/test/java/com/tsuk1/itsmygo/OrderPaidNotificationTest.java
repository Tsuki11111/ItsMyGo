package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.ChatMessage;
import com.tsuk1.itsmygo.entity.Conversation;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.ChatMessageMapper;
import com.tsuk1.itsmygo.mapper.ConversationMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付成功 → 通知商家发货（站内会话 SYSTEM 消息 + 商家未读 +1）。
 *
 * 覆盖：正常通知、重复支付不会再发、系统消息不计入顾客未读。
 */
@SpringBootTest
class OrderPaidNotificationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long createdCategoryId;
    private Long createdProductId;
    private Long createdSkuId;
    private Long orderId;
    private Long buyerId;

    private void preparePendingOrder() {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("PaidNotify" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        // 平台自营店：商家侧由管理员代管（会话推给管理员）
        product.setMerchantId(Merchant.PLATFORM_MERCHANT_ID);
        product.setName("PaidNotify " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("12.00"));
        product.setStock(20);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();
        createdSkuId = insertDefaultSku(product.getId(), product.getPrice(), product.getStock());

        User user = new User();
        user.setUsername("paidnotify" + stamp);
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setNickname("payer");
        user.setPhone("");
        user.setStatus(1);
        user.setRole(Roles.CUSTOMER);
        userMapper.insert(user);
        createdUserIds.add(user.getId());
        buyerId = user.getId();

        Address address = new Address();
        address.setUserId(buyerId);
        address.setReceiver("R");
        address.setPhone("13800000000");
        address.setProvince("P");
        address.setCity("C");
        address.setDistrict("D");
        address.setDetail("Detail");
        address.setIsDefault(1);
        addressMapper.insert(address);

        CartItem cartItem = new CartItem();
        cartItem.setUserId(buyerId);
        cartItem.setProductId(createdProductId);
        cartItem.setSkuId(createdSkuId);
        cartItem.setQuantity(1);
        cartItem.setSelected(1);
        cartItemMapper.insert(cartItem);

        orderId = orderService.create(buyerId,
                new CreateOrderRequest(address.getId(), orderService.issueToken(buyerId), null))
                .order().getId();
    }

    private Long insertDefaultSku(Long productId, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecValueIds("");
        sku.setSpecText("");
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setSales(0);
        sku.setImage("");
        sku.setStatus(1);
        skuMapper.insert(sku);
        return sku.getId();
    }

    private Conversation conversation() {
        return conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getCustomerUserId, buyerId)
                .eq(Conversation::getMerchantId, Merchant.PLATFORM_MERCHANT_ID));
    }

    private List<ChatMessage> systemMessages() {
        Conversation conversation = conversation();
        if (conversation == null) {
            return List.of();
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getSenderType, ChatMessage.SENDER_SYSTEM));
    }

    @Test
    void payNotifiesMerchantToShip() {
        preparePendingOrder();
        Order pending = orderMapper.selectById(orderId);
        String orderNo = pending.getOrderNo();

        orderService.pay(buyerId, orderId);

        Conversation conversation = conversation();
        assertThat(conversation).as("支付成功后应建立顾客—店铺会话").isNotNull();
        assertThat(conversation.getMerchantUnread()).as("商家未读应 +1").isGreaterThanOrEqualTo(1);
        assertThat(conversation.getCustomerUnread()).as("系统消息不算顾客未读").isZero();

        List<ChatMessage> messages = systemMessages();
        assertThat(messages).hasSize(1);
        ChatMessage message = messages.get(0);
        assertThat(message.getContent()).contains(orderNo).contains("请及时发货");
        assertThat(message.getSenderUserId()).isEqualTo(buyerId);
    }

    @Test
    void repeatedPayDoesNotNotifyTwice() {
        preparePendingOrder();

        orderService.pay(buyerId, orderId);
        assertThatThrownBy(() -> orderService.pay(buyerId, orderId))
                .isInstanceOf(BizException.class);

        assertThat(systemMessages()).as("重复支付被状态机拒绝，不应再发通知").hasSize(1);
    }

    /** 只删本次测试造出来的行（库是共享的测试库，绝不能 delete(null)） */
    @AfterEach
    void cleanup() {
        if (!createdUserIds.isEmpty()) {
            List<Long> orderIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .in(Order::getUserId, createdUserIds))
                    .stream().map(Order::getId).toList();
            List<Long> conversationIds = conversationMapper.selectList(
                            Wrappers.<Conversation>lambdaQuery()
                                    .in(Conversation::getCustomerUserId, createdUserIds))
                    .stream().map(Conversation::getId).toList();
            if (!conversationIds.isEmpty()) {
                chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                        .in(ChatMessage::getConversationId, conversationIds));
                conversationMapper.delete(Wrappers.<Conversation>lambdaQuery()
                        .in(Conversation::getId, conversationIds));
            }
            if (!orderIds.isEmpty()) {
                paymentMapper.delete(Wrappers.<Payment>lambdaQuery().in(Payment::getOrderId, orderIds));
                orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds));
                orderMapper.delete(Wrappers.<Order>lambdaQuery().in(Order::getId, orderIds));
            }
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().in(CartItem::getUserId, createdUserIds));
            addressMapper.delete(Wrappers.<Address>lambdaQuery().in(Address::getUserId, createdUserIds));
        }
        if (createdSkuId != null) {
            skuMapper.deleteById(createdSkuId);
            createdSkuId = null;
        }
        if (createdProductId != null) {
            productMapper.deleteById(createdProductId);
            createdProductId = null;
        }
        if (createdCategoryId != null) {
            categoryMapper.deleteById(createdCategoryId);
            createdCategoryId = null;
        }
        for (Long userId : createdUserIds) {
            userMapper.deleteById(userId);
        }
        createdUserIds.clear();
        orderId = null;
        buyerId = null;
    }
}
