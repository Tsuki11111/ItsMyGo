package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.service.OrderSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 消费「订单已创建」事件做异步补账（M31 / D1）。
 *
 * <p>补账失败：先做失败收尾（CREATE_FAILED + 回补 Redis/券），再 ack 掉这条消息，
 * 不无限重投；无法恢复的异常才 reject 不重投，交给日志/人工与扫描兜底。</p>
 */
@Slf4j
public class OrderCreatedConsumer {

    private final OrderSettlementService settlementService;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(OrderSettlementService settlementService,
                                ObjectMapper objectMapper) {
        this.settlementService = settlementService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderCreatedRabbitTopology.QUEUE)
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
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (RuntimeException e) {
            log.warn("忽略无法解析的订单创建事件: {}", payload);
            return;
        }
        List<Long> orderIds = event.orderIds() == null ? List.of() : event.orderIds();
        if (orderIds.isEmpty()) {
            return;
        }
        try {
            settlementService.settle(orderIds, event.cartItemIds());
        } catch (RuntimeException e) {
            log.warn("订单异步补账失败，转创建失败: orderIds={} err={}", orderIds, e.getMessage());
            try {
                settlementService.failCreation(orderIds, "补账失败：" + e.getMessage());
            } catch (RuntimeException compensationError) {
                log.error("订单创建失败收尾也失败，交给扫描兜底: orderIds={}", orderIds, compensationError);
            }
            // 已经做过失败收尾就 ack；不要无限重投
            throw new AmqpRejectAndDontRequeueException(
                    "订单补账失败: orderIds=" + orderIds);
        }
    }
}
