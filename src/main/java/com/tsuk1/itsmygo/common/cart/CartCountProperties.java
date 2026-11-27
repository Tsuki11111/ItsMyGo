package com.tsuk1.itsmygo.common.cart;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 购物车角标缓存配置。
 *
 * <p>TTL 只决定「角标最多旧多久」，不影响购物车页与下单：购物车页始终查库，
 * 下单也始终以数据库为准。所以这里可以放得比较长，用更少的数据库查询换更高的并发。</p>
 */
@ConfigurationProperties(prefix = "itsmygo.cart-count")
public class CartCountProperties {

    /** 缓存基础 TTL；实际写入时会加 ±20% 抖动，防止一批 key 同时过期 */
    private Duration ttl = Duration.ofMinutes(5);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
