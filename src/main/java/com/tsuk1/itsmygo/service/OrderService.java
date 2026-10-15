package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;

public interface OrderService {

    /** 结算下单：从购物车勾选项生成订单并扣库存 */
    OrderView create(Long userId, CreateOrderRequest request);

    PageResponse<Order> pageForCustomer(Long userId, int page, int size);

    OrderView detailForCustomer(Long userId, Long orderId);

    /** 模拟支付 */
    OrderView pay(Long userId, Long orderId);

    /** 取消订单（会回滚库存） */
    OrderView cancel(Long userId, Long orderId);

    OrderView confirmReceipt(Long userId, Long orderId);

    // ── 管理端 ──

    PageResponse<Order> pageForAdmin(int page, int size, String status);

    OrderView detailForAdmin(Long orderId);

    OrderView ship(Long orderId, ShipRequest request);
}
