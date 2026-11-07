package com.tsuk1.itsmygo.common.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 会话内表单的一次性消费：同一 {@code formId} 只允许转发给 agent 一次。
 *
 * <p>为什么是固定 TTL：gRPC 下发的 {@code FormResult} 只有 {@code form_id / values}，
 * Java 在这里拿不到表单本身的 {@code expires_in_seconds}，所以统一用
 * {@value #TTL_SECONDS} 秒（表单建议 600 秒 + 60 秒缓冲）；固定值也不会超过 1 小时。
 *
 * <p><b>Redis 故障必须降级</b>：消费失败记日志后照样放行，不能因为 Redis 不可用
 * 把整条对话链路堵死。降级期间可能重复提交一次，由 agent 侧的
 * {@code form_id} 校验与业务幂等兜底。
 */
@Slf4j
@Service
public class AgentFormGuard {

    private static final String KEY_PREFIX = "its-my-go:agent:form:";
    private static final long TTL_SECONDS = 660;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    private final StringRedisTemplate redis;

    public AgentFormGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 抢占某个表单的提交权。
     *
     * <p>只做一次原子的 SETNX，没有「先 EXISTS 再 SETNX」的竞态窗口；
     * 返回值不是明确的 {@code true} 时一律按「已提交」处理，宁可拒绝也不重复转发。
     *
     * @return true = 首次提交，可以继续转发；false = 已提交过；Redis 故障时也返回 true（降级放行）
     */
    public boolean tryConsume(long userId, String formId) {
        String key = key(userId, formId);
        try {
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", TTL);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("表单一次性消费 Redis 不可用，降级放行: userId={}, formId={}, err={}",
                    userId, formId, e.getMessage());
            return true;
        }
    }

    private String key(long userId, String formId) {
        return KEY_PREFIX + userId + ":" + formId;
    }
}
