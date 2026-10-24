package com.tsuk1.itsmygo.config;

import com.tsuk1.itsmygo.common.order.OrderTimeoutConsumer;
import com.tsuk1.itsmygo.common.order.OrderTimeoutProperties;
import com.tsuk1.itsmygo.common.order.OrderTimeoutRabbitTopology;
import com.tsuk1.itsmygo.common.order.OrderTimeoutSender;
import com.tsuk1.itsmygo.common.order.RabbitMqOrderTimeoutSender;
import com.tsuk1.itsmygo.service.OrderService;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 超时关单拓扑：延迟队列 TTL 到期后死信到工作队列。
 *
 * <p>只在 {@code itsmygo.order-timeout.mq-enabled=true} 时生效；测试与 CI 默认关闭，
 * 不依赖 RabbitMQ。RabbitMQ 未启动时监听器会自动重连，发送失败由扫描兜底。</p>
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "itsmygo.order-timeout", name = "mq-enabled",
        havingValue = "true")
public class OrderTimeoutRabbitConfig {

    @Bean
    public Declarables orderTimeoutTopology(OrderTimeoutProperties properties) {
        DirectExchange exchange = new DirectExchange(
                OrderTimeoutRabbitTopology.EXCHANGE, true, false);

        Queue delayQueue = QueueBuilder.durable(OrderTimeoutRabbitTopology.DELAY_QUEUE)
                // 队列级 TTL：所有消息超时时间一致，先进先过期，不会出现队头阻塞
                .ttl((int) (properties.minutes() * 60_000L))
                .deadLetterExchange(OrderTimeoutRabbitTopology.EXCHANGE)
                .deadLetterRoutingKey(OrderTimeoutRabbitTopology.WORK_ROUTING_KEY)
                .build();
        Queue workQueue = QueueBuilder.durable(OrderTimeoutRabbitTopology.WORK_QUEUE).build();

        return new Declarables(
                exchange,
                delayQueue,
                workQueue,
                BindingBuilder.bind(delayQueue).to(exchange)
                        .with(OrderTimeoutRabbitTopology.DELAY_ROUTING_KEY),
                BindingBuilder.bind(workQueue).to(exchange)
                        .with(OrderTimeoutRabbitTopology.WORK_ROUTING_KEY));
    }

    @Bean
    public OrderTimeoutSender orderTimeoutSender(RabbitTemplate rabbitTemplate) {
        return new RabbitMqOrderTimeoutSender(rabbitTemplate);
    }

    @Bean
    public OrderTimeoutConsumer orderTimeoutConsumer(OrderTimeoutProperties properties,
                                                     OrderService orderService) {
        return new OrderTimeoutConsumer(properties, orderService);
    }
}
