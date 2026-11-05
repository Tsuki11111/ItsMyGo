package com.tsuk1.itsmygo.common.after_sale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 售后超时策略（M30）。
 *
 * <p>售后量小、规则可能频繁调，所以不用 RabbitMQ 延迟消息，改用 {@code @Scheduled} 扫描
 * （与订单超时扫描同一模式，扫描间隔内处理有延迟，可接受）。</p>
 */
@ConfigurationProperties(prefix = "itsmygo.after-sale")
public record AfterSaleProperties(
        Boolean enabled,
        Integer reviewHours,
        Integer returnConfirmDays,
        Long scanIntervalMs,
        Long scanLockTtlSeconds) {

    public AfterSaleProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (reviewHours == null || reviewHours <= 0) {
            reviewHours = 48;
        }
        if (returnConfirmDays == null || returnConfirmDays <= 0) {
            returnConfirmDays = 7;
        }
        if (scanIntervalMs == null || scanIntervalMs <= 0) {
            scanIntervalMs = 300_000L;
        }
        if (scanLockTtlSeconds == null || scanLockTtlSeconds <= 0) {
            scanLockTtlSeconds = 240L;
        }
    }
}
