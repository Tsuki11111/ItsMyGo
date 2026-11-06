package com.tsuk1.itsmygo.config;

import com.tsuk1.itsmygo.common.order.OrderCreatedConsumer;
import com.tsuk1.itsmygo.common.order.OrderCreatedRabbitTopology;
import com.tsuk1.itsmygo.service.OrderSettlementService;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 下单异步补账 MQ 拓扑（M31 / D1）。
 *
 * <p>只在 {@code itsmygo.order-create.async=true} 时生效；测试与无 MQ 环境关闭，
 * 不依赖 RabbitMQ（同步补账降级由 OrderServiceImpl 直接调用结算服务）。</p>
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "itsmygo.order-create", name = "async", havingValue = "true")
public class OrderCreatedRabbitConfig {

    @Bean
    public Declarables orderCreatedTopology() {
        DirectExchange exchange = new DirectExchange(
                OrderCreatedRabbitTopology.EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(OrderCreatedRabbitTopology.QUEUE).build();
        return new Declarables(
                exchange,
                queue,
                BindingBuilder.bind(queue).to(exchange)
                        .with(OrderCreatedRabbitTopology.ROUTING_KEY));
    }

    @Bean
    public OrderCreatedConsumer orderCreatedConsumer(OrderSettlementService settlementService,
                                                     ObjectMapper objectMapper) {
        return new OrderCreatedConsumer(settlementService, objectMapper);
    }
}
