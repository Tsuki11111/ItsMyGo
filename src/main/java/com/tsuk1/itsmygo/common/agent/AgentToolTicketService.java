package com.tsuk1.itsmygo.common.agent;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * Agent 工具调用票据：每次 Converse 由 Java 签发，随 gRPC metadata 下发给 agent 服务。
 *
 * <p>agent 调用 {@code /internal/agent/**} 时把票据带回来，Java 只认票据里的 userId ——
 * **模型永远拿不到、也传不了用户身份**。票据在 Redis 里带 10 分钟 TTL，
 * 与 Java 侧 gRPC deadline（240 秒）留足余量。
 */
@Service
public class AgentToolTicketService {

    private static final String PREFIX = "its-my-go:agent:tool-ticket:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String SEP = "|";

    private final StringRedisTemplate redis;

    public AgentToolTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String issue(long userId, String sessionId) {
        String ticket = UUID.randomUUID().toString();
        String value = userId + SEP + (sessionId == null ? "" : sessionId);
        redis.opsForValue().set(PREFIX + ticket, value, TTL);
        return ticket;
    }

    /** @return 票据对应的身份；不存在 / 已过期返回 null */
    public Identity resolve(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String value = redis.opsForValue().get(PREFIX + ticket);
        if (value == null) {
            return null;
        }
        int sep = value.indexOf(SEP);
        if (sep < 0) {
            return null;
        }
        try {
            return new Identity(Long.parseLong(value.substring(0, sep)), value.substring(sep + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Identity(long userId, String sessionId) {
    }
}
