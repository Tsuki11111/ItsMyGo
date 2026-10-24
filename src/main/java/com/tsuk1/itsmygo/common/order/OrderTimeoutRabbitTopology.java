package com.tsuk1.itsmygo.common.order;

/** RabbitMQ 超时关单拓扑：延迟队列到期后死信到工作队列。 */
public final class OrderTimeoutRabbitTopology {

    public static final String EXCHANGE = "its-my-go-order-timeout-exchange";
    public static final String DELAY_QUEUE = "its-my-go-order-timeout-delay-queue";
    public static final String WORK_QUEUE = "its-my-go-order-timeout-work-queue";

    public static final String DELAY_ROUTING_KEY = "order-timeout-delay";
    public static final String WORK_ROUTING_KEY = "order-timeout-work";

    private OrderTimeoutRabbitTopology() {
    }
}
