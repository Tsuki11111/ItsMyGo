package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 消费超时关单消息：按 orderId 做幂等取消。
 *
 * <p>已支付 / 已取消 / 未到超时时间都正常 ack；数据库等临时故障直接 reject，
 * 不无限重投，交给数据库扫描兜底。</p>
 */
@Slf4j
public class OrderTimeoutConsumer {

    private final OrderTimeoutProperties properties;
    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderTimeoutProperties properties, OrderService orderService) {
        this.properties = properties;
        this.orderService = orderService;
    }

    @RabbitListener(queues = OrderTimeoutRabbitTopology.WORK_QUEUE)
    public void onMessage(Message message) {
        String traceId = (String) message.getMessageProperties().getHeaders()
                .get(RequestIdFilter.TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = "mq-" + UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(RequestIdFilter.TRACE_ID, traceId);
        try {
            handlePayload(new String(message.getBody(), StandardCharsets.UTF_8));
        } finally {
            MDC.remove(RequestIdFilter.TRACE_ID);
        }
    }

    void handlePayload(String payload) {
        long orderId;
        try {
            orderId = Long.parseLong(payload.trim());
        } catch (RuntimeException e) {
            log.warn("忽略无法解析的超时关单消息: {}", payload);
            return;
        }

        try {
            boolean cancelled = orderService.cancelIfExpired(orderId, properties.minutes());
            if (cancelled) {
                log.info("RabbitMQ 定时关单成功: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.warn("处理超时关单消息失败，交给扫描兜底: orderId={} err={}",
                    orderId, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "处理超时关单消息失败: orderId=" + orderId);
        }
    }
}
