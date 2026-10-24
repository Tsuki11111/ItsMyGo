package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutConsumerTest {

    private final OrderService orderService = mock(OrderService.class);

    @Test
    void cancelsAndAcksWhenOrderExpired() {
        when(orderService.cancelIfExpired(11L, 30)).thenReturn(true);

        assertThatCode(() -> consumer().handlePayload("11")).doesNotThrowAnyException();
        verify(orderService).cancelIfExpired(11L, 30);
    }

    @Test
    void alreadyPaidOrCancelledStillAcks() {
        when(orderService.cancelIfExpired(12L, 30)).thenReturn(false);

        assertThatCode(() -> consumer().handlePayload("12")).doesNotThrowAnyException();
    }

    @Test
    void databaseFailureRejectsWithoutRequeue() {
        when(orderService.cancelIfExpired(13L, 30)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> consumer().handlePayload("13"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void malformedPayloadIsAckedWithoutTouchingOrders() {
        assertThatCode(() -> consumer().handlePayload("not-a-number"))
                .doesNotThrowAnyException();
        verify(orderService, never()).cancelIfExpired(anyLong(), anyInt());
    }

    private OrderTimeoutConsumer consumer() {
        return new OrderTimeoutConsumer(
                new OrderTimeoutProperties(30, true, true, 300_000L, 240L),
                orderService);
    }
}
