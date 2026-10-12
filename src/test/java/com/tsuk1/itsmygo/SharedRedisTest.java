package com.tsuk1.itsmygo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共用 Redis 容器连通性。
 * key 前缀与 TTL 是硬约束：容器以 volatile-lru 策略运行，不带 TTL 的 key 在内存打满时会写失败。
 */
@SpringBootTest
class SharedRedisTest {

    private static final String KEY_PREFIX = "its-mygo:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void sharedRedisIsReachableAndKeyCarriesTtl() {
        String key = KEY_PREFIX + "smoke:" + System.currentTimeMillis();

        redisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(30));

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
        assertThat(redisTemplate.getExpire(key)).isPositive();

        redisTemplate.delete(key);
    }
}
