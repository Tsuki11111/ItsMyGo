package com.tsuk1.itsmygo.common.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** 把超时关单消息投到延迟队列；发送失败只记日志，数据库扫描兜底。 */
@Slf4j
public class RabbitMqOrderTimeoutSender implements OrderTimeoutSender {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqOrderTimeoutSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(long orderId, String orderNo) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderTimeoutRabbitTopology.EXCHANGE,
                    OrderTimeoutRabbitTopology.DELAY_ROUTING_KEY,
                    String.valueOf(orderId),
                    message -> {
                        message.getMessageProperties().setMessageId(orderNo);
                        return message;
                    });
            log.debug("已发送超时关单消息: orderNo={}", orderNo);
        } catch (RuntimeException e) {
            log.warn("发送超时关单消息失败，等待扫描兜底: orderNo={} err={}",
                    orderNo, e.getMessage());
        }
    }
}
