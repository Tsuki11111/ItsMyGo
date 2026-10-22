package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.entity.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutPublisherTest {

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsOnlyAfterCommit() {
        OrderTimeoutSender sender = mock(OrderTimeoutSender.class);
        OrderTimeoutPublisher publisher = new OrderTimeoutPublisher(providerOf(sender));
        Order order = order(1L, "NO1");

        TransactionSynchronizationManager.initSynchronization();
        publisher.publishAfterCommit(List.of(order));
        verify(sender, never()).send(anyLong(), anyString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(sender).send(1L, "NO1");
    }

    @Test
    void senderFailureDoesNotBreakPublishing() {
        OrderTimeoutSender sender = mock(OrderTimeoutSender.class);
        doThrow(new RuntimeException("broker down")).when(sender).send(anyLong(), anyString());
        OrderTimeoutPublisher publisher = new OrderTimeoutPublisher(providerOf(sender));

        TransactionSynchronizationManager.initSynchronization();
        publisher.publishAfterCommit(List.of(order(2L, "NO2")));

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
    }

    @Test
    void noSenderWhenMqDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OrderTimeoutSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        OrderTimeoutPublisher publisher = new OrderTimeoutPublisher(provider);

        assertThatCode(() -> publisher.publishAfterCommit(List.of(order(3L, "NO3"))))
                .doesNotThrowAnyException();
    }

    private static Order order(long id, String orderNo) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        return order;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OrderTimeoutSender> providerOf(OrderTimeoutSender sender) {
        ObjectProvider<OrderTimeoutSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
