package com.tsuk1.itsmygo.common.order;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitMqOrderTimeoutSenderTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void sendsOrderIdToDelayQueueWithOrderNoAsMessageId() {
        doNothing().when(rabbitTemplate).convertAndSend(
                eq(OrderTimeoutRabbitTopology.EXCHANGE),
                eq(OrderTimeoutRabbitTopology.DELAY_ROUTING_KEY),
                eq("42"),
                any(MessagePostProcessor.class));

        new RabbitMqOrderTimeoutSender(rabbitTemplate).send(42L, "NO42");

        ArgumentCaptor<MessagePostProcessor> captor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(OrderTimeoutRabbitTopology.EXCHANGE),
                eq(OrderTimeoutRabbitTopology.DELAY_ROUTING_KEY),
                eq("42"), captor.capture());

        Message message = captor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("NO42");
    }

    @Test
    void sendFailureDoesNotBreakOrderCreation() {
        doThrow(new AmqpException("rabbit down")).when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(Object.class),
                any(MessagePostProcessor.class));

        assertThatCode(() -> new RabbitMqOrderTimeoutSender(rabbitTemplate)
                .send(43L, "NO43")).doesNotThrowAnyException();
    }
}
