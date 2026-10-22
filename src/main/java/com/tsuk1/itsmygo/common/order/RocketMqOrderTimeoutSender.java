package com.tsuk1.itsmygo.common.order;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.context.SmartLifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocketMQ 5.x gRPC 生产者，发送延迟投递的订单超时消息。
 *
 * <p>启动失败不阻断应用：只记 warn，超时关单由数据库扫描兜底。</p>
 */
@Slf4j
public class RocketMqOrderTimeoutSender implements OrderTimeoutSender, SmartLifecycle {

    private final RocketMqProperties mqProperties;
    private final OrderTimeoutProperties timeoutProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ClientServiceProvider provider;
    private Producer producer;

    public RocketMqOrderTimeoutSender(RocketMqProperties mqProperties,
                                      OrderTimeoutProperties timeoutProperties) {
        this.mqProperties = mqProperties;
        this.timeoutProperties = timeoutProperties;
    }

    @Override
    public void start() {
        try {
            provider = ClientServiceProvider.loadService();
            ClientConfiguration configuration = ClientConfiguration.newBuilder()
                    .setEndpoints(mqProperties.endpoints())
                    .setRequestTimeout(Duration.ofSeconds(mqProperties.requestTimeoutSeconds()))
                    .build();
            producer = provider.newProducerBuilder()
                    .setClientConfiguration(configuration)
                    .setTopics(mqProperties.topic())
                    .build();
            running.set(true);
            log.info("RocketMQ 超时关单生产者已启动: endpoints={} topic={}",
                    mqProperties.endpoints(), mqProperties.topic());
        } catch (Exception e) {
            log.warn("RocketMQ 生产者启动失败，超时关单将依赖数据库扫描: {}", e.getMessage());
        }
    }

    @Override
    public void send(long orderId, String orderNo) {
        if (!running.get() || producer == null) {
            return;
        }
        try {
            Message message = provider.newMessageBuilder()
                    .setTopic(mqProperties.topic())
                    .setKeys(orderNo)
                    .setBody(String.valueOf(orderId).getBytes(StandardCharsets.UTF_8))
                    .setDeliveryTimestamp(System.currentTimeMillis()
                            + timeoutProperties.minutes() * 60_000L)
                    .build();
            producer.send(message);
            log.info("已发送超时关单消息: orderNo={} deliverAt={}", orderNo,
                    System.currentTimeMillis() + timeoutProperties.minutes() * 60_000L);
        } catch (Exception e) {
            log.warn("发送订单超时消息失败，等待扫描兜底: orderNo={} err={}",
                    orderNo, e.getMessage());
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                log.warn("关闭 RocketMQ 生产者失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
