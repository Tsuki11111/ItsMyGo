package com.tsuk1.itsmygo.common.chat;

import com.tsuk1.itsmygo.dto.ChatStreamTicketView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE 连接票据。EventSource 不能设置 Authorization，所以先用 JWT 换一个短期票据，
 * 再把它放进 URL。票据一次性消费，避免把 JWT 暴露在访问日志里。
 */
@Service
public class ChatTicketService {

    private static final String PREFIX = "its-my-go:chat:ticket:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public ChatTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public ChatStreamTicketView issue(Long userId) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + ticket, String.valueOf(userId), TTL);
        return new ChatStreamTicketView(ticket, TTL.toSeconds());
    }

    /** @return 票据对应的 userId；不存在/已使用/过期时返回 null */
    public Long consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String value = redis.opsForValue().getAndDelete(PREFIX + ticket);
        return value == null ? null : Long.valueOf(value);
    }
}
