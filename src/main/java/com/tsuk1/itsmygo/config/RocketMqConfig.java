package com.tsuk1.itsmygo.config;

import com.tsuk1.itsmygo.common.order.OrderTimeoutConsumer;
import com.tsuk1.itsmygo.common.order.OrderTimeoutProperties;
import com.tsuk1.itsmygo.common.order.OrderTimeoutSender;
import com.tsuk1.itsmygo.common.order.RocketMqOrderTimeoutSender;
import com.tsuk1.itsmygo.common.order.RocketMqProperties;
import com.tsuk1.itsmygo.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 超时关单的生产者 / 消费者装配。
 *
 * <p>只在 {@code itsmygo.order-timeout.mq-enabled=true} 时生效；测试与 CI 默认关闭，
 * 不依赖 RocketMQ。启动失败由各自的 SmartLifecycle 吞掉，扫描兜底仍工作。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "itsmygo.order-timeout", name = "mq-enabled",
        havingValue = "true")
public class RocketMqConfig {

    @Bean
    public OrderTimeoutSender orderTimeoutSender(RocketMqProperties mqProperties,
                                                 OrderTimeoutProperties timeoutProperties) {
        return new RocketMqOrderTimeoutSender(mqProperties, timeoutProperties);
    }

    @Bean
    public OrderTimeoutConsumer orderTimeoutConsumer(RocketMqProperties mqProperties,
                                                     OrderTimeoutProperties timeoutProperties,
                                                     OrderService orderService) {
        return new OrderTimeoutConsumer(mqProperties, timeoutProperties, orderService);
    }
}
