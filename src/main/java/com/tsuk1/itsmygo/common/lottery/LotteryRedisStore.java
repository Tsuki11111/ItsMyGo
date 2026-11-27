package com.tsuk1.itsmygo.common.lottery;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 抽奖模块的 Redis Set 封装。
 *
 * <p>只负责「快速判断 + 实时库存」，不承担最终事实：中奖记录以 MySQL 为准。</p>
 *
 * <p>所有 key 都必须带 TTL（共用 Redis 是 volatile-lru）。所以新增成员统一走
 * {@code SADD + EXPIRE} 的 Lua，避免「先 SADD、还没 EXPIRE 就崩溃」留下不带 TTL 的 key。</p>
 */
@Component
public class LotteryRedisStore {

    private static final String PREFIX = "its-my-go:lottery:";

    /**
     * KEYS[1] = Set key，ARGV[1] = TTL 秒，ARGV[2..] = 要加入的成员。
     * 返回本次真正新增的成员数（SADD 的返回值之和），调用方用它判断「是不是第一次」。
     */
    private static final RedisScript<Long> ADD_WITH_TTL = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local ttl = tonumber(ARGV[1])
            local added = 0
            for i = 2, #ARGV do
                added = added + redis.call('SADD', key, ARGV[i])
            end
            redis.call('EXPIRE', key, ttl)
            return added
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public LotteryRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 标记用户已抽过奖；返回 true 表示这次是第一次加入 */
    public boolean markParticipant(Long activityId, Long userId, long ttlSeconds) {
        Long added = addWithTtl(participantsKey(activityId), ttlSeconds, String.valueOf(userId));
        return added != null && added > 0;
    }

    /** 快速判断用户是否抽过；真正兜底仍靠 MySQL 的 uk_activity_user */
    public boolean isParticipant(Long activityId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet()
                .isMember(participantsKey(activityId), String.valueOf(userId)));
    }

    /** 奖池剩余数量 */
    public long remainingStock(Long activityId, Long prizeId) {
        Long size = redisTemplate.opsForSet().size(prizePoolKey(activityId, prizeId));
        return size == null ? 0 : size;
    }

    /** 随机抽走一张奖券；奖池空了返回 null */
    public String takePrize(Long activityId, Long prizeId) {
        return redisTemplate.opsForSet().pop(prizePoolKey(activityId, prizeId));
    }

    /** 抽奖失败时把奖券放回奖池（比如 MySQL 插入失败） */
    public void returnPrize(Long activityId, Long prizeId, String prizeCode, long ttlSeconds) {
        addWithTtl(prizePoolKey(activityId, prizeId), ttlSeconds, prizeCode);
    }

    /** 初始化奖池：批量加入奖券码，返回本次新增数量 */
    public int seedPrizeCodes(Long activityId, Long prizeId, List<String> codes, long ttlSeconds) {
        if (codes.isEmpty()) {
            return 0;
        }
        Long added = addWithTtl(prizePoolKey(activityId, prizeId), ttlSeconds,
                codes.toArray(new String[0]));
        return added == null ? 0 : added.intValue();
    }

    /** 中奖名单缓存（MySQL 才是权威，丢了可重建） */
    public void markWinner(Long activityId, Long userId, long ttlSeconds) {
        addWithTtl(winnersKey(activityId), ttlSeconds, String.valueOf(userId));
    }

    public boolean isPrizePoolInitialized(Long activityId, Long prizeId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(initializedKey(activityId, prizeId)));
    }

    public void markPrizePoolInitialized(Long activityId, Long prizeId, long ttlSeconds) {
        redisTemplate.opsForValue().set(initializedKey(activityId, prizeId), "1",
                Duration.ofSeconds(ttlSeconds));
    }

    /** 初始化锁：保证同一活动的奖池只被一个请求灌入，避免并发重复 SADD */
    public boolean tryInitLock(Long activityId, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(initLockKey(activityId), "1", ttl));
    }

    public void releaseInitLock(Long activityId) {
        redisTemplate.delete(initLockKey(activityId));
    }

    /**
     * 活动结束后再保留 1 天方便对账；至少给 60 秒，保证 TTL 永远为正。
     */
    public long ttlSeconds(LocalDateTime endAt) {
        long seconds = Duration.between(LocalDateTime.now(), endAt.plusDays(1)).toSeconds();
        return Math.max(60, seconds);
    }

    private Long addWithTtl(String key, long ttlSeconds, String... members) {
        Object[] args = new Object[members.length + 1];
        args[0] = String.valueOf(ttlSeconds);
        System.arraycopy(members, 0, args, 1, members.length);
        return redisTemplate.execute(ADD_WITH_TTL, List.of(key), args);
    }

    private String participantsKey(Long activityId) {
        return PREFIX + "participants:" + activityId;
    }

    private String prizePoolKey(Long activityId, Long prizeId) {
        return PREFIX + "prize-pool:" + activityId + ":" + prizeId;
    }

    private String winnersKey(Long activityId) {
        return PREFIX + "winners:" + activityId;
    }

    private String initializedKey(Long activityId, Long prizeId) {
        return PREFIX + "initialized:" + activityId + ":" + prizeId;
    }

    private String initLockKey(Long activityId) {
        return PREFIX + "init-lock:" + activityId;
    }
}
