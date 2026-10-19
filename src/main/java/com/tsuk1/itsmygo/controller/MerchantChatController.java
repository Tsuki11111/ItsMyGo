package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.ChatMessagePage;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.ChatReadRequest;
import com.tsuk1.itsmygo.dto.ChatUnreadView;
import com.tsuk1.itsmygo.dto.ConversationView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.SendChatMessageRequest;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家侧聊天：只能查看/回复本店会话，**没有创建会话接口**，
 * 从协议层保证商家不能主动私信顾客。
 */
@RestController
@RequestMapping("/api/merchant/chat")
public class MerchantChatController {

    private final ChatService chatService;

    public MerchantChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public Result<PageResponse<ConversationView>> conversations(@AuthenticationPrincipal CurrentUser user,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "30") int size) {
        return Result.ok(chatService.merchantConversations(user, page, size));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationView> conversation(@AuthenticationPrincipal CurrentUser user,
                                                 @PathVariable Long id) {
        return Result.ok(chatService.merchantConversation(user, id));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<ChatMessagePage> messages(@AuthenticationPrincipal CurrentUser user,
                                            @PathVariable Long id,
                                            @RequestParam(required = false) Long beforeId,
                                            @RequestParam(required = false) Long afterId,
                                            @RequestParam(defaultValue = "30") int size) {
        return Result.ok(chatService.merchantMessages(user, id, beforeId, afterId, size));
    }

    @RateLimit(scene = "chat-send", limit = 30, windowSeconds = 60, byUser = true)
    @PostMapping("/conversations/{id}/messages")
    public Result<ChatMessageView> send(@AuthenticationPrincipal CurrentUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody SendChatMessageRequest request) {
        return Result.ok(chatService.merchantSend(user, id, request));
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Long> read(@AuthenticationPrincipal CurrentUser user,
                             @PathVariable Long id,
                             @Valid @RequestBody ChatReadRequest request) {
        return Result.ok(chatService.merchantRead(user, id, request.lastMessageId()));
    }

    @GetMapping("/unread-count")
    public Result<ChatUnreadView> unread(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(chatService.unread(user));
    }
}
