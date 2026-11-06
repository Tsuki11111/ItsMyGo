package com.tsuk1.itsmygo.common.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下单异步补账（M31 / D1）参数。
 *
 * <p>{@code async=false} 时退回同步补账（与旧链路等价），测试与无 MQ 环境用它；
 * 生产默认 {@code async=true}，下单请求只落占位订单，补账交给 RabbitMQ 消费端。</p>
 */
@ConfigurationProperties("itsmygo.order-create")
public record OrderCreateProperties(
        boolean async,
        boolean scanEnabled,
        int creatingTimeoutSeconds,
        long scanIntervalMs,
        int scanLockTtlSeconds,
        int maxRepublish) {

    public OrderCreateProperties {
        if (creatingTimeoutSeconds <= 0) {
            creatingTimeoutSeconds = 120;
        }
        if (scanIntervalMs <= 0) {
            scanIntervalMs = 60_000L;
        }
        if (scanLockTtlSeconds <= 0) {
            scanLockTtlSeconds = 240;
        }
        if (maxRepublish <= 0) {
            maxRepublish = 3;
        }
    }
}
