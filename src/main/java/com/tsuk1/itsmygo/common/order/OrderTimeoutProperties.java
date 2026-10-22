package com.tsuk1.itsmygo.common.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 超时未支付订单的取消策略：MQ 定时消息 + 数据库扫描兜底。 */
@ConfigurationProperties(prefix = "itsmygo.order-timeout")
public record OrderTimeoutProperties(
        Integer minutes,
        Boolean mqEnabled,
        Boolean scanEnabled,
        Long scanIntervalMs,
        Long scanLockTtlSeconds) {

    public OrderTimeoutProperties {
        if (minutes == null || minutes <= 0) {
            minutes = 30;
        }
        // RocketMQ timer 消息默认最多 3 天；超时阈值超过它就别指望 MQ 能按时投递了
        if (minutes > 4320) {
            throw new IllegalArgumentException("order-timeout.minutes 不能超过 4320（3 天）");
        }
        if (mqEnabled == null) {
            mqEnabled = false;
        }
        if (scanEnabled == null) {
            scanEnabled = true;
        }
        if (scanIntervalMs == null || scanIntervalMs <= 0) {
            scanIntervalMs = 300_000L;
        }
        if (scanLockTtlSeconds == null || scanLockTtlSeconds <= 0) {
            scanLockTtlSeconds = 240L;
        }
    }
}
