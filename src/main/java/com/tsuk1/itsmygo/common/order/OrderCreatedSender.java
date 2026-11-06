package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 发送「订单已创建」事件到 RabbitMQ。
 *
 * <p>发送失败只记日志，不阻断下单；`CREATING` 超时扫描会重投兜底
 * （见 {@link com.tsuk1.itsmygo.common.order.OrderCreationScanJob}）。</p>
 */
@Slf4j
@Component
public class OrderCreatedSender {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedSender(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(OrderCreatedEvent event) {
        try {
            String traceId = MDC.get(RequestIdFilter.TRACE_ID);
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(
                    OrderCreatedRabbitTopology.EXCHANGE,
                    OrderCreatedRabbitTopology.ROUTING_KEY,
                    payload,
                    message -> {
                        message.getMessageProperties().setMessageId("order-created-" + event.orderIds());
                        if (StringUtils.hasText(traceId)) {
                            message.getMessageProperties().setHeader(
                                    RequestIdFilter.TRACE_HEADER, traceId);
                        }
                        return message;
                    });
            log.debug("已发送订单创建事件: orderIds={}", event.orderIds());
        } catch (RuntimeException e) {
            log.warn("发送订单创建事件失败，等待 CREATING 超时扫描重投: orderIds={} err={}",
                    event.orderIds(), e.getMessage());
        }
    }
}
