package com.tsuk1.itsmygo.common.order;

import com.tsuk1.itsmygo.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 在订单事务提交后发送超时关单的定时消息。
 *
 * <p>发送失败只记日志，不影响下单 —— 数据库扫描会兜底。消息可能重复，
 * 消费端按订单状态幂等处理。</p>
 */
@Slf4j
@Component
public class OrderTimeoutPublisher {

    private final ObjectProvider<OrderTimeoutSender> senderProvider;

    public OrderTimeoutPublisher(ObjectProvider<OrderTimeoutSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    public void publishAfterCommit(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Runnable publish = () -> {
            OrderTimeoutSender sender = senderProvider.getIfAvailable();
            if (sender == null) {
                log.debug("超时关单 MQ 未启用，跳过发送: orders={}", orders.size());
                return;
            }
            log.debug("开始发送超时关单消息: orders={}", orders.size());
            for (Order order : orders) {
                try {
                    sender.send(order.getId(), order.getOrderNo());
                } catch (RuntimeException e) {
                    log.warn("发送超时关单消息失败，等待扫描兜底: orderNo={} err={}",
                            order.getOrderNo(), e.getMessage());
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
