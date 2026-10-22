package com.tsuk1.itsmygo.common.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RocketMQ 5.x gRPC 客户端与超时关单主题的配置。 */
@ConfigurationProperties(prefix = "itsmygo.rocketmq")
public record RocketMqProperties(
        String endpoints,
        String topic,
        String producerGroup,
        String consumerGroup,
        Integer requestTimeoutSeconds) {

    public RocketMqProperties {
        if (endpoints == null || endpoints.isBlank()) {
            endpoints = "127.0.0.1:8081";
        }
        if (topic == null || topic.isBlank()) {
            topic = "its-my-go-order-timeout";
        }
        if (producerGroup == null || producerGroup.isBlank()) {
            producerGroup = "its-my-go-order-timeout-producer";
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            consumerGroup = "its-my-go-order-timeout-consumer";
        }
        if (requestTimeoutSeconds == null || requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 3;
        }
    }
}
