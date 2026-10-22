package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutScanJobTest {

    private final OrderService orderService = mock(OrderService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    @Test
    void scansOnlyWhenLockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        job().scan();

        verify(orderService).cancelExpired(30);
    }

    @Test
    void skipsWhenAnotherInstanceHoldsLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        job().scan();

        verify(orderService, never()).cancelExpired(anyInt());
    }

    @Test
    void skipsWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        job().scan();

        verify(orderService, never()).cancelExpired(anyInt());
    }

    private OrderTimeoutScanJob job() {
        return new OrderTimeoutScanJob(orderService, redisTemplate,
                new OrderTimeoutProperties(30, true, true, 300_000L, 240L));
    }
}
