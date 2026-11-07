package com.tsuk1.itsmygo.common.agent;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 表单一次性消费：首次放行 / 重复拒绝 / Redis 故障降级放行。 */
class AgentFormGuardTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final AgentFormGuard guard = new AgentFormGuard(redis);

    @Test
    void firstSubmissionIsConsumedWithFixedTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent("its-my-go:agent:form:7:f1", "1", Duration.ofSeconds(660)))
                .thenReturn(true);

        assertThat(guard.tryConsume(7L, "f1")).isTrue();
    }

    @Test
    void repeatedSubmissionIsRejected() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(660))))
                .thenReturn(false);

        assertThat(guard.tryConsume(7L, "f1")).isFalse();
    }

    @Test
    void redisFailureDegradesToAllow() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(660))))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // Redis 不可用不能阻断业务：降级放行，由 agent 侧幂等兜底
        assertThat(guard.tryConsume(7L, "f1")).isTrue();
    }
}
