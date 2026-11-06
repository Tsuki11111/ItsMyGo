package com.tsuk1.itsmygo.common.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** 事务提交后再发「订单已创建」事件；发送失败不阻断下单，由扫描兜底重投。 */
@Slf4j
@Component
public class OrderCreatedMqPublisher {

    private final OrderCreatedSender sender;

    public OrderCreatedMqPublisher(OrderCreatedSender sender) {
        this.sender = sender;
    }

    public void publishAfterCommit(List<Long> orderIds, List<Long> cartItemIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        OrderCreatedEvent event = new OrderCreatedEvent(List.copyOf(orderIds),
                cartItemIds == null ? List.of() : List.copyOf(cartItemIds));
        Runnable publish = () -> sender.send(event);
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
