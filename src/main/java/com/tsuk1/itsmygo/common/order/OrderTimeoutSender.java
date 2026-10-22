package com.tsuk1.itsmygo.common.order;

/** 超时关单消息的发送端口；MQ 关闭时不注册实现。 */
public interface OrderTimeoutSender {

    void send(long orderId, String orderNo);
}
