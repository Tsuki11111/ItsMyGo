package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.common.chat.ChatEvent;
import com.tsuk1.itsmygo.common.chat.ChatStreamRegistry;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ChatCard;
import com.tsuk1.itsmygo.dto.ChatMessagePage;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ChatUnreadView;
import com.tsuk1.itsmygo.dto.ConversationView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.SendChatMessageRequest;
import com.tsuk1.itsmygo.dto.StartConversationRequest;
import com.tsuk1.itsmygo.entity.ChatMessage;
import com.tsuk1.itsmygo.entity.Conversation;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.ChatMessageMapper;
import com.tsuk1.itsmygo.mapper.ConversationMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ChatService;
import com.tsuk1.itsmygo.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MESSAGE_SIZE = 30;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int PREVIEW_LENGTH = 60;

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final StorageService storageService;
    private final ChatStreamRegistry registry;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(ConversationMapper conversationMapper, ChatMessageMapper chatMessageMapper,
                           UserMapper userMapper, MerchantMapper merchantMapper,
                           ProductMapper productMapper, OrderMapper orderMapper,
                           OrderItemMapper orderItemMapper, StorageService storageService,
                           ChatStreamRegistry registry, ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.storageService = storageService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ConversationView startConversation(CurrentUser user, StartConversationRequest request) {
        requireNotAdmin(user);
        if (request.productId() != null && request.orderId() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次只能附带一种卡片");
        }

        Merchant merchant = merchantMapper.selectById(request.merchantId());
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "店铺不存在或已停用");
        }
        if (merchant.getOwnerUserId() == null
                && merchant.getId() != Merchant.PLATFORM_MERCHANT_ID) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺暂未绑定商家账号");
        }
        if (user.merchantId() != null && user.merchantId().equals(merchant.getId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能联系自己的店铺");
        }

        int inserted = conversationMapper.insertIgnore(user.id(), merchant.getId());
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getCustomerUserId, user.id())
                .eq(Conversation::getMerchantId, merchant.getId()));
        if (conversation == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "会话创建失败");
        }

        // 只在新建会话时附带卡片，避免每次点「联系卖家」都重复发卡片
        if (inserted > 0 && request.productId() != null) {
            ChatCard.Product card = buildProductCard(request.productId(), merchant.getId());
            appendMessage(conversation, user.id(), ChatMessage.SENDER_CUSTOMER,
                    ChatMessage.TYPE_PRODUCT, card.name(), objectMapper.writeValueAsString(card));
        } else if (inserted > 0 && request.orderId() != null) {
            ChatCard.Order card = buildOrderCard(request.orderId(), conversation);
            appendMessage(conversation, user.id(), ChatMessage.SENDER_CUSTOMER,
                    ChatMessage.TYPE_ORDER, card.orderNo(), objectMapper.writeValueAsString(card));
        }

        Conversation fresh = conversationMapper.selectById(conversation.getId());
        User customer = userMapper.selectById(fresh.getCustomerUserId());
        return toConversationView(fresh, Conversation.SIDE_CUSTOMER,
                registry.isMerchantOnline(fresh.getMerchantId()), merchant, customer);
    }

    @Override
    public PageResponse<ConversationView> customerConversations(CurrentUser user, int page, int size) {
        requireNotAdmin(user);
        Page<Conversation> result = conversationMapper.selectPage(page(page, size),
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getCustomerUserId, user.id())
                        .isNotNull(Conversation::getLastMessageId)
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getId));
        return toConversationPage(result, Conversation.SIDE_CUSTOMER);
    }

    @Override
    public ConversationView customerConversation(CurrentUser user, Long conversationId) {
        requireNotAdmin(user);
        Conversation conversation = requireCustomerConversation(conversationId, user.id());
        return detailView(conversation, Conversation.SIDE_CUSTOMER);
    }

    @Override
    public ChatMessagePage customerMessages(CurrentUser user, Long conversationId,
                                            Long beforeId, Long afterId, int size) {
        requireNotAdmin(user);
        Conversation conversation = requireCustomerConversation(conversationId, user.id());
        return messagePage(conversation, Conversation.SIDE_CUSTOMER, beforeId, afterId, size);
    }

    @Override
    @Transactional
    public ChatMessageView customerSend(CurrentUser user, Long conversationId,
                                        SendChatMessageRequest request) {
        requireNotAdmin(user);
        Conversation conversation = requireCustomerConversation(conversationId, user.id());
        return send(conversation, Conversation.SIDE_CUSTOMER, user.id(), request);
    }

    @Override
    @Transactional
    public long customerRead(CurrentUser user, Long conversationId, Long lastMessageId) {
        requireNotAdmin(user);
        Conversation conversation = requireCustomerConversation(conversationId, user.id());
        return markRead(conversation, Conversation.SIDE_CUSTOMER, lastMessageId);
    }

    @Override
    public ChatUnreadView unread(CurrentUser user) {
        if (user.isAdmin()) {
            // 平台自营店的商家侧未读由管理员共同承担
            return new ChatUnreadView(0,
                    conversationMapper.sumMerchantUnread(Merchant.PLATFORM_MERCHANT_ID));
        }
        requireNotAdmin(user);
        long customerUnread = conversationMapper.sumCustomerUnread(user.id());
        long merchantUnread = user.isMerchant()
                ? conversationMapper.sumMerchantUnread(user.merchantId()) : 0;
        return new ChatUnreadView(customerUnread, merchantUnread);
    }

    @Override
    public PageResponse<ConversationView> merchantConversations(CurrentUser user, int page, int size) {
        requireMerchant(user);
        Page<Conversation> result = conversationMapper.selectPage(page(page, size),
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getMerchantId, user.merchantId())
                        .isNotNull(Conversation::getLastMessageId)
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getId));
        return toConversationPage(result, Conversation.SIDE_MERCHANT);
    }

    @Override
    public ConversationView merchantConversation(CurrentUser user, Long conversationId) {
        requireMerchant(user);
        Conversation conversation = requireMerchantConversation(conversationId, user.merchantId());
        return detailView(conversation, Conversation.SIDE_MERCHANT);
    }

    @Override
    public ChatMessagePage merchantMessages(CurrentUser user, Long conversationId,
                                            Long beforeId, Long afterId, int size) {
        requireMerchant(user);
        Conversation conversation = requireMerchantConversation(conversationId, user.merchantId());
        return messagePage(conversation, Conversation.SIDE_MERCHANT, beforeId, afterId, size);
    }

    @Override
    @Transactional
    public ChatMessageView merchantSend(CurrentUser user, Long conversationId,
                                        SendChatMessageRequest request) {
        requireMerchant(user);
        Conversation conversation = requireMerchantConversation(conversationId, user.merchantId());
        return send(conversation, Conversation.SIDE_MERCHANT, user.id(), request);
    }

    @Override
    @Transactional
    public long merchantRead(CurrentUser user, Long conversationId, Long lastMessageId) {
        requireMerchant(user);
        Conversation conversation = requireMerchantConversation(conversationId, user.merchantId());
        return markRead(conversation, Conversation.SIDE_MERCHANT, lastMessageId);
    }

    // ── 平台自营店：管理员以商家身份处理会话 ──

    @Override
    public PageResponse<ConversationView> adminConversations(CurrentUser user, int page, int size) {
        requireAdmin(user);
        Page<Conversation> result = conversationMapper.selectPage(page(page, size),
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getMerchantId, Merchant.PLATFORM_MERCHANT_ID)
                        .isNotNull(Conversation::getLastMessageId)
                        .orderByDesc(Conversation::getLastMessageAt)
                        .orderByDesc(Conversation::getId));
        return toConversationPage(result, Conversation.SIDE_MERCHANT);
    }

    @Override
    public ConversationView adminConversation(CurrentUser user, Long conversationId) {
        requireAdmin(user);
        Conversation conversation = requirePlatformConversation(conversationId);
        return detailView(conversation, Conversation.SIDE_MERCHANT);
    }

    @Override
    public ChatMessagePage adminMessages(CurrentUser user, Long conversationId,
                                         Long beforeId, Long afterId, int size) {
        requireAdmin(user);
        Conversation conversation = requirePlatformConversation(conversationId);
        return messagePage(conversation, Conversation.SIDE_MERCHANT, beforeId, afterId, size);
    }

    @Override
    @Transactional
    public ChatMessageView adminSend(CurrentUser user, Long conversationId,
                                     SendChatMessageRequest request) {
        requireAdmin(user);
        Conversation conversation = requirePlatformConversation(conversationId);
        return send(conversation, Conversation.SIDE_MERCHANT, user.id(), request);
    }

    @Override
    @Transactional
    public long adminRead(CurrentUser user, Long conversationId, Long lastMessageId) {
        requireAdmin(user);
        Conversation conversation = requirePlatformConversation(conversationId);
        return markRead(conversation, Conversation.SIDE_MERCHANT, lastMessageId);
    }

    // ── 发送 / 已读 ──

    private ChatMessageView send(Conversation conversation, String senderType, Long senderUserId,
                                 SendChatMessageRequest request) {
        Merchant merchant = merchantMapper.selectById(conversation.getMerchantId());
        if (merchant == null || !Merchant.ACTIVE.equals(merchant.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "店铺已停用，暂时无法发送消息");
        }

        String type = request.type() == null ? "" : request.type().trim().toUpperCase();
        String content;
        String cardData = null;
        switch (type) {
            case ChatMessage.TYPE_TEXT -> {
                if (!StringUtils.hasText(request.text())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "消息内容不能为空");
                }
                content = request.text().trim();
                if (content.length() > MAX_TEXT_LENGTH) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "消息不能超过 " + MAX_TEXT_LENGTH + " 字");
                }
            }
            case ChatMessage.TYPE_IMAGE -> {
                if (!storageService.isChatImageUrl(request.imageUrl())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "图片地址不合法，请重新上传");
                }
                content = request.imageUrl();
            }
            case ChatMessage.TYPE_PRODUCT -> {
                if (request.productId() == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "缺少商品 ID");
                }
                ChatCard.Product card = buildProductCard(request.productId(), conversation.getMerchantId());
                content = card.name();
                cardData = objectMapper.writeValueAsString(card);
            }
            case ChatMessage.TYPE_ORDER -> {
                if (request.orderId() == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "缺少订单 ID");
                }
                ChatCard.Order card = buildOrderCard(request.orderId(), conversation);
                content = card.orderNo();
                cardData = objectMapper.writeValueAsString(card);
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的消息类型");
        }

        ChatMessage message = appendMessage(conversation, senderUserId, senderType, type, content, cardData);
        return toMessageView(message);
    }

    private long markRead(Conversation conversation, String side, Long lastMessageId) {
        if (lastMessageId == null || lastMessageId <= 0) {
            return side.equals(Conversation.SIDE_CUSTOMER)
                    ? conversation.getCustomerUnread() : conversation.getMerchantUnread();
        }
        long lastId = conversation.getLastMessageId() == null ? 0 : conversation.getLastMessageId();
        long effective = Math.min(lastMessageId, lastId);

        if (side.equals(Conversation.SIDE_CUSTOMER)) {
            conversationMapper.advanceCustomerRead(conversation.getId(), effective);
            conversationMapper.recountCustomerUnread(conversation.getId());
        } else {
            conversationMapper.advanceMerchantRead(conversation.getId(), effective);
            conversationMapper.recountMerchantUnread(conversation.getId());
        }

        Conversation fresh = conversationMapper.selectById(conversation.getId());
        long unread = Conversation.SIDE_CUSTOMER.equals(side)
                ? conversationMapper.sumCustomerUnread(fresh.getCustomerUserId())
                : conversationMapper.sumMerchantUnread(fresh.getMerchantId());

        Merchant merchant = merchantMapper.selectById(conversation.getMerchantId());
        List<Long> peerUserIds = Conversation.SIDE_CUSTOMER.equals(side)
                ? merchantRecipients(merchant)
                : List.of(conversation.getCustomerUserId());
        if (!peerUserIds.isEmpty()) {
            String readerType = side;
            Long conversationId = conversation.getId();
            afterCommit(() -> {
                for (Long peerUserId : peerUserIds) {
                    registry.sendToUser(peerUserId, ChatEvent.READ,
                            new ChatEvent.Read(conversationId, readerType, effective));
                }
            });
        }
        return unread;
    }

    private ChatMessage appendMessage(Conversation conversation, Long senderUserId, String senderType,
                                      String contentType, String content, String cardData) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(senderUserId);
        message.setSenderType(senderType);
        message.setContentType(contentType);
        message.setContent(content);
        message.setCardData(cardData);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        String preview = preview(contentType, content);
        conversationMapper.updateLastMessage(conversation.getId(), message.getId(), preview,
                message.getCreatedAt());
        boolean fromCustomer = Conversation.SIDE_CUSTOMER.equals(senderType);
        conversationMapper.addUnread(conversation.getId(),
                fromCustomer ? 0 : 1, fromCustomer ? 1 : 0);

        Long conversationId = conversation.getId();
        Long messageId = message.getId();
        afterCommit(() -> pushMessage(conversationId, messageId));
        return message;
    }

    private void pushMessage(Long conversationId, Long messageId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (conversation == null || message == null) {
            return;
        }
        Merchant merchant = merchantMapper.selectById(conversation.getMerchantId());
        User customer = userMapper.selectById(conversation.getCustomerUserId());
        ChatMessageView view = toMessageView(message);

        if (customer != null) {
            ConversationView customerView = toConversationView(conversation,
                    Conversation.SIDE_CUSTOMER, registry.isMerchantOnline(conversation.getMerchantId()),
                    merchant, customer);
            registry.sendToUser(customer.getId(), ChatEvent.MESSAGE,
                    new ChatEvent.Message(Conversation.SIDE_CUSTOMER, view, customerView,
                            conversationMapper.sumCustomerUnread(customer.getId())));
        }
        List<Long> recipients = merchantRecipients(merchant);
        if (merchant != null && !recipients.isEmpty()) {
            ConversationView merchantView = toConversationView(conversation,
                    Conversation.SIDE_MERCHANT, false, merchant, customer);
            long unread = conversationMapper.sumMerchantUnread(merchant.getId());
            for (Long recipient : recipients) {
                registry.sendToUser(recipient, ChatEvent.MESSAGE,
                        new ChatEvent.Message(Conversation.SIDE_MERCHANT, view, merchantView, unread));
            }
        }
    }

    // ── 查询与组装 ──

    private ChatMessagePage messagePage(Conversation conversation, String side,
                                        Long beforeId, Long afterId, int size) {
        int limit = normalizeSize(size);
        List<ChatMessage> rows = chatMessageMapper.selectRecent(conversation.getId(),
                beforeId, afterId, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<ChatMessageView> records = rows.stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
        java.util.Collections.reverse(records);
        Long peerLastRead = Conversation.SIDE_CUSTOMER.equals(side)
                ? conversation.getMerchantLastReadMessageId()
                : conversation.getCustomerLastReadMessageId();
        return new ChatMessagePage(records, hasMore, peerLastRead);
    }

    private PageResponse<ConversationView> toConversationPage(Page<Conversation> result, String side) {
        List<Conversation> records = result.getRecords();
        Map<Long, Merchant> merchants = loadMerchants(records.stream()
                .map(Conversation::getMerchantId).toList());
        Map<Long, User> customers = loadUsers(records.stream()
                .map(Conversation::getCustomerUserId).toList());
        List<ConversationView> views = records.stream()
                .map(conversation -> toConversationView(conversation, side,
                        Conversation.SIDE_CUSTOMER.equals(side)
                                && registry.isMerchantOnline(conversation.getMerchantId()),
                        merchants.get(conversation.getMerchantId()),
                        customers.get(conversation.getCustomerUserId())))
                .toList();
        return new PageResponse<>(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private ConversationView detailView(Conversation conversation, String side) {
        Merchant merchant = merchantMapper.selectById(conversation.getMerchantId());
        User customer = userMapper.selectById(conversation.getCustomerUserId());
        return toConversationView(conversation, side,
                Conversation.SIDE_CUSTOMER.equals(side)
                        && registry.isMerchantOnline(conversation.getMerchantId()),
                merchant, customer);
    }

    private ConversationView toConversationView(Conversation conversation, String side,
                                                boolean peerOnline, Merchant merchant, User customer) {
        long unread = Conversation.SIDE_CUSTOMER.equals(side)
                ? value(conversation.getCustomerUnread())
                : value(conversation.getMerchantUnread());
        return new ConversationView(
                conversation.getId(),
                conversation.getMerchantId(),
                merchant == null ? "" : merchant.getName(),
                merchant == null ? "" : merchant.getLogo(),
                conversation.getCustomerUserId(),
                customerName(customer),
                customer == null ? "" : customer.getAvatar(),
                conversation.getLastMessageId(),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageAt(),
                unread,
                peerOnline,
                conversation.getCreatedAt());
    }

    private ChatMessageView toMessageView(ChatMessage message) {
        JsonNode card = message.getCardData() == null ? null : objectMapper.readTree(message.getCardData());
        return new ChatMessageView(message.getId(), message.getConversationId(),
                message.getSenderUserId(), message.getSenderType(), message.getContentType(),
                message.getContent(), card, message.getCreatedAt());
    }

    // ── 卡片 ──

    private ChatCard.Product buildProductCard(Long productId, Long merchantId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Objects.equals(product.getMerchantId(), merchantId)) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return new ChatCard.Product(product.getId(), product.getName(), product.getMainImage(),
                product.getPrice(), product.getMerchantId(), product.getStatus());
    }

    private ChatCard.Order buildOrderCard(Long orderId, Conversation conversation) {
        Order order = orderMapper.selectById(orderId);
        if (order == null
                || !Objects.equals(order.getMerchantId(), conversation.getMerchantId())
                || !Objects.equals(order.getUserId(), conversation.getCustomerUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderItem first = orderItemMapper.selectOne(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId)
                .last("LIMIT 1"));
        String statusLabel;
        try {
            statusLabel = OrderStatus.valueOf(order.getStatus()).label();
        } catch (IllegalArgumentException e) {
            statusLabel = order.getStatus();
        }
        return new ChatCard.Order(order.getId(), order.getOrderNo(), order.getStatus(), statusLabel,
                order.getTotalAmount(),
                first == null ? "" : first.getProductName(),
                first == null ? "" : first.getProductImage());
    }

    // ── 权限与工具 ──

    private Conversation requireCustomerConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getCustomerUserId(), userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private Conversation requireMerchantConversation(Long conversationId, Long merchantId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getMerchantId(), merchantId)) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private void requireNotAdmin(CurrentUser user) {
        if (user == null || user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "管理员请从管理后台处理平台消息");
        }
    }

    private void requireMerchant(CurrentUser user) {
        if (user == null || !user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "没有商家权限");
        }
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "没有管理员权限");
        }
    }

    private Conversation requirePlatformConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !Objects.equals(conversation.getMerchantId(), Merchant.PLATFORM_MERCHANT_ID)) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    /**
     * 商家侧消息接收人：普通店铺是店主；平台自营店没有 owner，由全部管理员共同处理。
     */
    private List<Long> merchantRecipients(Merchant merchant) {
        if (merchant == null) {
            return List.of();
        }
        if (merchant.getOwnerUserId() != null) {
            return List.of(merchant.getOwnerUserId());
        }
        if (Objects.equals(merchant.getId(), Merchant.PLATFORM_MERCHANT_ID)) {
            return userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .eq(User::getRole, Roles.ADMIN)
                            .eq(User::getStatus, 1))
                    .stream().map(User::getId).toList();
        }
        return List.of();
    }

    private Map<Long, Merchant> loadMerchants(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return merchantMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String customerName(User user) {
        if (user == null) return "";
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String preview(String type, String content) {
        return switch (type) {
            case ChatMessage.TYPE_IMAGE -> "[图片]";
            case ChatMessage.TYPE_PRODUCT -> "[商品] " + content;
            case ChatMessage.TYPE_ORDER -> "[订单] " + content;
            default -> content.length() <= PREVIEW_LENGTH
                    ? content : content.substring(0, PREVIEW_LENGTH) + "…";
        };
    }

    private Page<Conversation> page(int page, int size) {
        int current = Math.max(1, page);
        return new Page<>(current, normalizeSize(size));
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_MESSAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long value(Integer number) {
        return number == null ? 0 : number;
    }

    /** 推送只在事务提交后执行；推送失败不影响已落库的消息 */
    private void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("聊天事件推送失败: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
