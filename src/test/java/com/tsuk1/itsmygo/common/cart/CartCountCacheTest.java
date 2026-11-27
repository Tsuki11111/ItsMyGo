package com.tsuk1.itsmygo.common.cart;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 缓存是可选加速层：Redis 出问题时购物车角标必须还能用 */
class CartCountCacheTest {

    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final CartCountCache cache =
            new CartCountCache(redisTemplate, new CartCountProperties());

    @Test
    void redisFailureFallsBackToLoader() {
        Mockito.when(redisTemplate.opsForHash())
                .thenThrow(new RedisConnectionFailureException("redis down"));

        AtomicInteger loads = new AtomicInteger();
        long count = cache.count(1L, () -> {
            loads.incrementAndGet();
            return new CartCountSnapshot(7, Map.of(10L, 7));
        });

        assertThat(count).isEqualTo(7);
        assertThat(loads).hasValue(1);
    }

    @Test
    void evictFailureDoesNotBreakCartWrites() {
        Mockito.when(redisTemplate.delete(Mockito.anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> cache.evict(1L)).doesNotThrowAnyException();
    }
}
