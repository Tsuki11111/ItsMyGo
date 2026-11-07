package com.tsuk1.itsmygo.common.promotion;

import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.service.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 智能推广的触发点：**用户登录成功后**，在 0~N 秒（默认 300）内随机延迟发送。
 *
 * 为什么不用固定 cron：用户量大时同一个时刻扫全量，DB/聊天/SSE 会出现瞬时尖峰；
 * 登录触发天然把流量摊到全天（用户什么时候登录就什么时候推），且只触达活跃用户。
 *
 * 去重三层：
 * 1. 按「用户 + 日期」在 Redis SETNX，同一用户当天只排一次任务（Redis 故障则降级放行）；
 * 2. 任务执行时 {@link PromotionService#notifyForUser(long)} 再查偏好与 last_promotion_date；
 * 3. 发送成功才写 last_promotion_date（每天最多 1 条）。
 */
@Slf4j
@Component
public class PromotionLoginTrigger {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String KEY_PREFIX = "its-my-go:promotion:scheduled:";
    private static final Duration KEY_TTL = Duration.ofHours(26);

    private final PromotionService promotionService;
    private final AgentPreferenceMapper preferenceMapper;
    private final StringRedisTemplate redis;
    private final ScheduledThreadPoolExecutor executor;
    private final int delaySeconds;
    private final boolean enabled;

    public PromotionLoginTrigger(PromotionService promotionService,
                                 AgentPreferenceMapper preferenceMapper,
                                 StringRedisTemplate redis,
                                 @Qualifier("promotionExecutor") ScheduledThreadPoolExecutor executor,
                                 @Value("${itsmygo.promotion.login-delay-seconds:300}") int delaySeconds,
                                 @Value("${itsmygo.promotion.enabled:true}") boolean enabled) {
        this.promotionService = promotionService;
        this.preferenceMapper = preferenceMapper;
        this.redis = redis;
        this.executor = executor;
        this.delaySeconds = Math.max(0, delaySeconds);
        this.enabled = enabled;
    }

    /** 登录成功时调用；任何异常都不能影响登录本身 */
    public void onLogin(long userId) {
        if (!enabled) {
            return;
        }
        try {
            LocalDate today = LocalDate.now(SHANGHAI);
            AgentPreference preference = preferenceMapper.selectById(userId);
            if (preference == null || !Boolean.TRUE.equals(preference.getPromotionEnabled())) {
                return;
            }
            if (today.equals(preference.getLastPromotionDate())) {
                return;
            }
            if (!markScheduled(userId, today)) {
                return;
            }
            long delay = delaySeconds <= 0 ? 0
                    : ThreadLocalRandom.current().nextLong(0, delaySeconds + 1L);
            executor.schedule(() -> {
                try {
                    promotionService.notifyForUser(userId);
                } catch (RuntimeException e) {
                    log.warn("智能推广延迟任务失败：userId={}, err={}", userId, e.getMessage());
                }
            }, delay, TimeUnit.SECONDS);
            log.info("智能推广已排期：userId={}，{} 秒后发送（随机抖动）", userId, delay);
        } catch (RuntimeException e) {
            // 推广问题不允许影响登录
            log.warn("智能推广排期失败（不影响登录）：userId={}, err={}", userId, e.getMessage());
        }
    }

    /** 同一用户当天只排一次；Redis 故障时降级为「允许排期」（靠 last_promotion_date 兜底） */
    private boolean markScheduled(long userId, LocalDate today) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    KEY_PREFIX + userId + ":" + today, "1", KEY_TTL);
            return Boolean.TRUE.equals(first);
        } catch (RuntimeException e) {
            log.warn("智能推广排期去重 Redis 不可用，降级放行：userId={}", userId);
            return true;
        }
    }
}
