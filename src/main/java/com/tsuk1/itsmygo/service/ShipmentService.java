package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.LogisticsView;
import com.tsuk1.itsmygo.entity.Order;

/** 虚拟物流：发货时生成运单，读取时按时间推进状态。 */
public interface ShipmentService {

    void createForOrder(Order order);

    /** @return 未发货 / 没有运单时返回 null */
    LogisticsView viewForOrder(Order order);

    void cancelForOrder(Long orderId);
}
