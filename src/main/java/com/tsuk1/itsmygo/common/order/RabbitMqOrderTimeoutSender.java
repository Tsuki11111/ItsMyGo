package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.StringUtils;

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
            String traceId = MDC.get(RequestIdFilter.TRACE_ID);
            rabbitTemplate.convertAndSend(
                    OrderTimeoutRabbitTopology.EXCHANGE,
                    OrderTimeoutRabbitTopology.DELAY_ROUTING_KEY,
                    String.valueOf(orderId),
                    message -> {
                        message.getMessageProperties().setMessageId(orderNo);
                        if (StringUtils.hasText(traceId)) {
                            message.getMessageProperties().setHeader(
                                    RequestIdFilter.TRACE_HEADER, traceId);
                        }
                        return message;
                    });
            log.debug("已发送超时关单消息: orderNo={}", orderNo);
        } catch (RuntimeException e) {
            log.warn("发送超时关单消息失败，等待扫描兜底: orderNo={} err={}",
                    orderNo, e.getMessage());
        }
    }
}
