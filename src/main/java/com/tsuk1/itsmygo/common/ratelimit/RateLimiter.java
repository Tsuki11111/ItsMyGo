package com.tsuk1.itsmygo.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定窗口计数限流。
 *
 * 窗口边界处可能放行到接近 2 倍配额（第 59 秒打满、第 61 秒又能打满）——
 * 对「防刷单、防爆破」这类用途完全可以接受；真要精确得用滑动窗口（ZSET 记时间戳），
 * 代价是每个请求都要在 ZSET 里存一个成员。
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "its-my-go:rate:";

    /**
     * INCR + 首次设 TTL + 判断，三步必须在一条脚本里完成：
     * 分开写的话，「INCR 之后、EXPIRE 之前」进程挂掉会留下**永不过期的计数键**，
     * 那个场景的配额就永远卡死了。
     */
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
                return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return true 放行，false 超限 */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        try {
            Long result = redisTemplate.execute(SCRIPT, List.of(KEY_PREFIX + key),
                    String.valueOf(windowSeconds), String.valueOf(limit));
            return result != null && result == 1L;
        } catch (Exception e) {
            // **fail-open**：限流是保护措施，不该因为 Redis 抖了一下就把整个站点拒之门外
            log.warn("限流检查失败，本次放行: key={} err={}", key, e.getMessage());
            return true;
        }
    }
}
