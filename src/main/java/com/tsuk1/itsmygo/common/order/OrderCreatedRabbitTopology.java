package com.tsuk1.itsmygo.common.order;

/** 下单异步补账 MQ 拓扑（M31 / D1）：普通直连交换机 + 一个持久化队列。 */
public final class OrderCreatedRabbitTopology {

    public static final String EXCHANGE = "its-my-go-order-created-exchange";
    public static final String QUEUE = "its-my-go-order-created-queue";
    public static final String ROUTING_KEY = "order-created";

    private OrderCreatedRabbitTopology() {
    }
}
