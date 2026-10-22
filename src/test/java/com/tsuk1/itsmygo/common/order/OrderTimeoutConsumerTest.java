package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.service.OrderService;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutConsumerTest {

    private final OrderService orderService = mock(OrderService.class);

    @Test
    void cancelsAndAcksWhenOrderExpired() {
        when(orderService.cancelIfExpired(11L, 30)).thenReturn(true);

        assertThat(consumer().handlePayload("11")).isEqualTo(ConsumeResult.SUCCESS);
        verify(orderService).cancelIfExpired(11L, 30);
    }

    @Test
    void alreadyPaidOrCancelledStillAcks() {
        when(orderService.cancelIfExpired(12L, 30)).thenReturn(false);

        assertThat(consumer().handlePayload("12")).isEqualTo(ConsumeResult.SUCCESS);
    }

    @Test
    void databaseFailureAsksForRedelivery() {
        when(orderService.cancelIfExpired(13L, 30)).thenThrow(new RuntimeException("db down"));

        assertThat(consumer().handlePayload("13")).isEqualTo(ConsumeResult.FAILURE);
    }

    @Test
    void malformedPayloadIsAckedWithoutTouchingOrders() {
        assertThat(consumer().handlePayload("not-a-number")).isEqualTo(ConsumeResult.SUCCESS);
        verify(orderService, never()).cancelIfExpired(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private OrderTimeoutConsumer consumer() {
        return new OrderTimeoutConsumer(
                new RocketMqProperties("127.0.0.1:8081", "topic", "pg", "cg", 3),
                new OrderTimeoutProperties(30, true, true, 300_000L, 240L),
                orderService);
    }
}
