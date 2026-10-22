package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.context.SmartLifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消费超时关单消息：按 orderId 做幂等取消。
 *
 * <p>订单已支付 / 已取消 / 还没到超时时间都直接 ack；只有数据库等临时故障才
 * 返回重试，交给 RocketMQ 重投。</p>
 */
@Slf4j
public class OrderTimeoutConsumer implements SmartLifecycle {

    private final RocketMqProperties mqProperties;
    private final OrderTimeoutProperties timeoutProperties;
    private final OrderService orderService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private PushConsumer consumer;

    public OrderTimeoutConsumer(RocketMqProperties mqProperties,
                                OrderTimeoutProperties timeoutProperties,
                                OrderService orderService) {
        this.mqProperties = mqProperties;
        this.timeoutProperties = timeoutProperties;
        this.orderService = orderService;
    }

    @Override
    public void start() {
        try {
            ClientServiceProvider provider = ClientServiceProvider.loadService();
            ClientConfiguration configuration = ClientConfiguration.newBuilder()
                    .setEndpoints(mqProperties.endpoints())
                    .setRequestTimeout(Duration.ofSeconds(mqProperties.requestTimeoutSeconds()))
                    .build();
            consumer = provider.newPushConsumerBuilder()
                    .setClientConfiguration(configuration)
                    .setConsumerGroup(mqProperties.consumerGroup())
                    .setSubscriptionExpressions(Map.of(
                            mqProperties.topic(), new FilterExpression("*")))
                    .setMessageListener(this::onMessage)
                    .build();
            running.set(true);
            log.info("RocketMQ 超时关单消费者已启动: endpoints={} topic={} group={}",
                    mqProperties.endpoints(), mqProperties.topic(), mqProperties.consumerGroup());
        } catch (Exception e) {
            log.warn("RocketMQ 消费者启动失败，超时关单将依赖数据库扫描: {}", e.getMessage());
        }
    }

    private ConsumeResult onMessage(MessageView message) {
        String payload = StandardCharsets.UTF_8.decode(message.getBody()).toString();
        return handlePayload(payload);
    }

    ConsumeResult handlePayload(String payload) {
        long orderId;
        try {
            orderId = Long.parseLong(payload.trim());
        } catch (RuntimeException e) {
            // 非法消息重投也没用，ack 掉并留日志
            log.warn("忽略无法解析的超时关单消息: {}", payload);
            return ConsumeResult.SUCCESS;
        }

        try {
            boolean cancelled = orderService.cancelIfExpired(orderId, timeoutProperties.minutes());
            if (cancelled) {
                log.info("RocketMQ 定时关单成功: orderId={}", orderId);
            }
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.warn("处理超时关单消息失败，等待重投: orderId={} err={}",
                    orderId, e.getMessage());
            return ConsumeResult.FAILURE;
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("关闭 RocketMQ 消费者失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
