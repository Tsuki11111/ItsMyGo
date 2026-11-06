package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.service.OrderSettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/** 订单创建事件的消费语义：补账失败要转失败收尾并 ack，不无限重投。 */
class OrderCreatedConsumerTest {

    private final OrderSettlementService settlementService =
            Mockito.mock(OrderSettlementService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderCreatedConsumer consumer =
            new OrderCreatedConsumer(settlementService, objectMapper);

    @Test
    void settlesOrdersAndCartItems() {
        consumer.handlePayload("""
                {"orderIds":[1,2],"cartItemIds":[9]}
                """);

        Mockito.verify(settlementService).settle(List.of(1L, 2L), List.of(9L));
        Mockito.verify(settlementService, Mockito.never()).failCreation(anyList(), anyString());
    }

    @Test
    void compensationThenRejectWhenSettlementFails() {
        Mockito.doThrow(new IllegalStateException("库存不足"))
                .when(settlementService).settle(anyList(), anyList());

        assertThatThrownBy(() -> consumer.handlePayload("""
                        {"orderIds":[7],"cartItemIds":[]}
                        """))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        Mockito.verify(settlementService).failCreation(eq(List.of(7L)), anyString());
    }

    @Test
    void ignoresUnparseablePayload() {
        consumer.handlePayload("not-json");

        Mockito.verifyNoInteractions(settlementService);
    }
}
