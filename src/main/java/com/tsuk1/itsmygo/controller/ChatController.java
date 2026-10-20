package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.chat.ChatEvent;
import com.tsuk1.itsmygo.common.chat.ChatStreamRegistry;
import com.tsuk1.itsmygo.common.chat.ChatTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.ChatMessagePage;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ChatReadRequest;
import com.tsuk1.itsmygo.dto.ChatStreamTicketView;
import com.tsuk1.itsmygo.dto.ChatUnreadView;
import com.tsuk1.itsmygo.dto.ConversationView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.SendChatMessageRequest;
import com.tsuk1.itsmygo.dto.StartConversationRequest;
import com.tsuk1.itsmygo.security.AuthIdentityService;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ChatService;
import com.tsuk1.itsmygo.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 顾客侧聊天。商家回复走 {@link MerchantChatController}。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatTicketService ticketService;
    private final ChatStreamRegistry registry;
    private final AuthIdentityService authIdentityService;
    private final StorageService storageService;

    public ChatController(ChatService chatService, ChatTicketService ticketService,
                          ChatStreamRegistry registry, AuthIdentityService authIdentityService,
                          StorageService storageService) {
        this.chatService = chatService;
        this.ticketService = ticketService;
        this.registry = registry;
        this.authIdentityService = authIdentityService;
        this.storageService = storageService;
    }

    @RateLimit(scene = "chat-create", limit = 10, windowSeconds = 60, byUser = true)
    @PostMapping("/conversations")
    public Result<ConversationView> start(@AuthenticationPrincipal CurrentUser user,
                                          @Valid @RequestBody StartConversationRequest request) {
        return Result.ok(chatService.startConversation(user, request));
    }

    @GetMapping("/conversations")
    public Result<PageResponse<ConversationView>> conversations(@AuthenticationPrincipal CurrentUser user,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "30") int size) {
        return Result.ok(chatService.customerConversations(user, page, size));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationView> conversation(@AuthenticationPrincipal CurrentUser user,
                                                 @PathVariable Long id) {
        return Result.ok(chatService.customerConversation(user, id));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<ChatMessagePage> messages(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable Long id,
                                            @RequestParam(required = false) Long beforeId,
                                            @RequestParam(required = false) Long afterId,
                                            @RequestParam(defaultValue = "30") int size) {
        return Result.ok(chatService.customerMessages(user, id, beforeId, afterId, size));
    }

    @RateLimit(scene = "chat-send", limit = 30, windowSeconds = 60, byUser = true)
    @PostMapping("/conversations/{id}/messages")
    public Result<ChatMessageView> send(@AuthenticationPrincipal CurrentUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody SendChatMessageRequest request) {
        return Result.ok(chatService.customerSend(user, id, request));
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Long> read(@AuthenticationPrincipal CurrentUser user,
                             @PathVariable Long id,
                             @Valid @RequestBody ChatReadRequest request) {
        return Result.ok(chatService.customerRead(user, id, request.lastMessageId()));
    }

    @GetMapping("/unread-count")
    public Result<ChatUnreadView> unread(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(chatService.unread(user));
    }

    @RateLimit(scene = "chat-ticket", limit = 20, windowSeconds = 60, byUser = true)
    @PostMapping("/stream-ticket")
    public Result<ChatStreamTicketView> ticket(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(ticketService.issue(user.id()));
    }

    /**
     * SSE 流。SecurityConfig 精确放行该路径，身份由一次性票据决定 ——
     * EventSource 不能设置 Authorization 头。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        Long userId = ticketService.consume(ticket);
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "连接票据无效或已过期");
        }
        CurrentUser identity = authIdentityService.load(userId);
        if (identity == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }

        SseEmitter emitter = registry.register(identity);
        ChatUnreadView unread = chatService.unread(identity);
        registry.sendToUser(identity.id(), ChatEvent.CONNECTED,
                new ChatEvent.Connected(identity.id(), unread.customerUnread(), unread.merchantUnread()));
        return emitter;
    }

    @RateLimit(scene = "chat-upload", limit = 20, windowSeconds = 60, byUser = true)
    @PostMapping("/files/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(storageService.uploadChatImage(file));
    }
}
