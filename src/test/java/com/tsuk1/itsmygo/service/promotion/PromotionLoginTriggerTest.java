package com.tsuk1.itsmygo.service.promotion;

import com.tsuk1.itsmygo.entity.AgentPreference;
import com.tsuk1.itsmygo.mapper.AgentPreferenceMapper;
import com.tsuk1.itsmygo.service.PromotionService;
import com.tsuk1.itsmygo.service.promotion.PromotionLoginTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * 登录触发回归：不按固定时间扫全量，改为「登录后 0~N 秒随机延迟」。
 *
 * 这里只验证排期策略（是否排、延迟范围、去重降级），真正发送由 PromotionServiceTest 覆盖。
 */
class PromotionLoginTriggerTest {

    private final PromotionService promotionService = mock(PromotionService.class);
    private final AgentPreferenceMapper preferenceMapper = mock(AgentPreferenceMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);

    private PromotionLoginTrigger trigger(int delaySeconds, boolean enabled) {
        when(redis.opsForValue()).thenReturn(valueOps);
        return new PromotionLoginTrigger(promotionService, preferenceMapper, redis, executor,
                delaySeconds, enabled);
    }

    private AgentPreference preference(boolean enabled, LocalDate lastDate) {
        AgentPreference preference = new AgentPreference();
        preference.setUserId(42L);
        preference.setPromotionEnabled(enabled);
        preference.setLastPromotionDate(lastDate);
        return preference;
    }

    @Test
    void schedulesWithRandomDelayWithinConfiguredWindow() {
        PromotionLoginTrigger trigger = trigger(300, true);
        when(preferenceMapper.selectById(42L)).thenReturn(preference(true, null));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        trigger.onLogin(42L);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(executor).schedule(task.capture(), delay.capture(), eq(TimeUnit.SECONDS));
        assertThat(delay.getValue()).isBetween(0L, 300L);
        // 任务执行时才真正发送（Redis 去重只负责当天排一次）
        task.getValue().run();
        verify(promotionService).notifyForUser(42L);
    }

    @Test
    void skipsWhenPromotionDisabled() {
        PromotionLoginTrigger trigger = trigger(300, true);
        when(preferenceMapper.selectById(42L)).thenReturn(preference(false, null));
        // Redis 允许排期：确保「跳过」真的是因为偏好关闭，而不是被去重兜住
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        trigger.onLogin(42L);

        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void skipsWhenAlreadySentToday() {
        PromotionLoginTrigger trigger = trigger(300, true);
        when(preferenceMapper.selectById(42L)).thenReturn(preference(true, LocalDate.now()));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        trigger.onLogin(42L);

        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void skipsWhenAlreadyScheduledToday() {
        PromotionLoginTrigger trigger = trigger(300, true);
        when(preferenceMapper.selectById(42L)).thenReturn(preference(true, null));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        trigger.onLogin(42L);

        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void stillSchedulesWhenRedisIsDown() {
        PromotionLoginTrigger trigger = trigger(300, true);
        when(preferenceMapper.selectById(42L)).thenReturn(preference(true, null));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        trigger.onLogin(42L);

        verify(executor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void respectsFeatureSwitch() {
        PromotionLoginTrigger trigger = trigger(300, false);
        trigger.onLogin(42L);
        verify(preferenceMapper, never()).selectById(anyLong());
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }
}
