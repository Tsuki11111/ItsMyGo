package com.tsuk1.itsmygo.common.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 虚拟物流配置：delivery-minutes 决定 ETA，也决定模拟多久后送达。 */
@ConfigurationProperties(prefix = "itsmygo.logistics")
public record LogisticsProperties(Integer deliveryMinutes) {

    public LogisticsProperties {
        if (deliveryMinutes == null || deliveryMinutes <= 0) {
            deliveryMinutes = 120;
        }
    }
}
