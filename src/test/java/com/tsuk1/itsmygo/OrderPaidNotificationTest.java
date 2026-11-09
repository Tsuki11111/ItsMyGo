package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.ChatMessage;
import com.tsuk1.itsmygo.entity.Conversation;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Notification;
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
import com.tsuk1.itsmygo.mapper.NotificationMapper;
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
    private NotificationMapper notificationMapper;
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
        // 支付后还会给顾客发一张订单确认卡（MERCHANT 发），故顾客未读 = 1
        assertThat(conversation.getCustomerUnread()).as("订单确认卡让顾客未读 +1").isEqualTo(1);

        List<ChatMessage> messages = systemMessages();
        assertThat(messages).hasSize(1);
        ChatMessage message = messages.get(0);
        assertThat(message.getContent()).contains(orderNo).contains("请及时发货");
        assertThat(message.getSenderUserId()).isEqualTo(buyerId);
    }

    @Test
    void shipNotifiesBuyerWithOrderLink() {
        preparePendingOrder();
        orderService.pay(buyerId, orderId);
        String orderNo = orderMapper.selectById(orderId).getOrderNo();

        orderService.ship(orderId, new ShipRequest("SF" + orderId));

        List<Notification> notifications = notificationMapper.selectList(
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, buyerId)
                        .eq(Notification::getType, "ORDER_SHIPPED"));
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getTitle()).isEqualTo("订单已发货");
        assertThat(notification.getContent()).contains(orderNo).contains("SF" + orderId);
        assertThat(notification.getLink()).isEqualTo("/order/" + orderId);
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void repeatedPayDoesNotNotifyTwice() {
        preparePendingOrder();

        orderService.pay(buyerId, orderId);
        assertThatThrownBy(() -> orderService.pay(buyerId, orderId))
                .isInstanceOf(BizException.class);

        assertThat(systemMessages()).as("重复支付被状态机拒绝，不应再发通知").hasSize(1);
    }

    private List<ChatMessage> messagesOfType(String contentType) {
        Conversation conversation = conversation();
        if (conversation == null) {
            return List.of();
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getContentType, contentType));
    }

    @Test
    void paySendsOrderConfirmCardToCustomer() {
        preparePendingOrder();
        String orderNo = orderMapper.selectById(orderId).getOrderNo();

        orderService.pay(buyerId, orderId);

        List<ChatMessage> cards = messagesOfType(ChatMessage.TYPE_ORDER_CONFIRM);
        assertThat(cards).hasSize(1);
        ChatMessage card = cards.get(0);
        assertThat(card.getSenderType()).isEqualTo(ChatMessage.SENDER_MERCHANT);
        assertThat(card.getContent()).isEqualTo(orderNo);
        assertThat(card.getCardData()).contains("\"orderId\":" + orderId);
    }

    @Test
    void buyerCanChangeAddressBeforeShipment() {
        preparePendingOrder();
        orderService.pay(buyerId, orderId);

        Address second = new Address();
        second.setUserId(buyerId);
        second.setReceiver("新收货人");
        second.setPhone("13900000000");
        second.setProvince("浙江省");
        second.setCity("杭州市");
        second.setDistrict("西湖区");
        second.setDetail("文三路 1 号");
        second.setIsDefault(0);
        addressMapper.insert(second);

        orderService.updateShippingAddress(buyerId, orderId, second.getId());

        Order order = orderMapper.selectById(orderId);
        assertThat(order.getReceiver()).isEqualTo("新收货人");
        assertThat(order.getAddress()).contains("文三路 1 号");
        assertThat(systemMessages())
                .anyMatch(m -> m.getContent().contains("已修改收货地址")
                        && m.getContent().contains("文三路 1 号"));
    }

    @Test
    void addressChangeRejectedAfterShipment() {
        preparePendingOrder();
        orderService.pay(buyerId, orderId);
        // 模拟已发货（真实链路是商家 ship 接口，这里直接改状态即可验证守卫）
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .set(Order::getStatus, "SHIPPED"));

        Address any = addressMapper.selectOne(Wrappers.<Address>lambdaQuery()
                .eq(Address::getUserId, buyerId).last("LIMIT 1"));
        assertThatThrownBy(() -> orderService.updateShippingAddress(buyerId, orderId, any.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能");
    }

    @Test
    void addressChangeRejectedForOtherUsersAddress() {
        preparePendingOrder();
        orderService.pay(buyerId, orderId);

        User other = new User();
        other.setUsername("other" + System.nanoTime());
        other.setPassword(passwordEncoder.encode("secret123"));
        other.setNickname("other");
        other.setPhone("");
        other.setStatus(1);
        other.setRole(Roles.CUSTOMER);
        userMapper.insert(other);
        createdUserIds.add(other.getId());
        Address stolen = new Address();
        stolen.setUserId(other.getId());
        stolen.setReceiver("别人");
        stolen.setPhone("13700000000");
        stolen.setProvince("P");
        stolen.setCity("C");
        stolen.setDistrict("D");
        stolen.setDetail("x");
        stolen.setIsDefault(1);
        addressMapper.insert(stolen);

        assertThatThrownBy(() -> orderService.updateShippingAddress(buyerId, orderId, stolen.getId()))
                .isInstanceOf(BizException.class);
    }

    /** 只删本次测试造出来的行（库是共享的测试库，绝不能 delete(null)） */
    @AfterEach
    void cleanup() {
        if (!createdUserIds.isEmpty()) {
            notificationMapper.delete(Wrappers.<Notification>lambdaQuery()
                    .in(Notification::getUserId, createdUserIds));
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
