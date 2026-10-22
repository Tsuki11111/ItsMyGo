package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.CheckoutView;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.LogisticsView;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;

import java.util.List;

public interface OrderService {

    /**
     * 领取一次性提交令牌 —— 进入结算页时调用，下单时带上。
     * 这是**防重复提交**的核心：用户连点两次「下单」只会成功一次。
     */
    String issueToken(Long userId);

    /** 结算下单：按商户拆成多个子订单，各自支付/发货/退款 */
    CheckoutView create(Long userId, CreateOrderRequest request);

    /**
     * 顾客端订单列表（**带明细**）。
     *
     * @param previewLimit 每个订单最多返回几条明细；≤0 表示不截断。
     *                     列表页传 3 就够（卡片上只显示前几件 + 「共 N 件」），
     *                     避免把几十条明细全传过去
     */
    /** 不带状态过滤的旧签名，保留给测试与内部调用 */
    default PageResponse<OrderView> pageForCustomer(Long userId, int page, int size, int previewLimit) {
        return pageForCustomer(userId, page, size, previewLimit, null);
    }

    /**
     * @param status 逗号分隔的订单状态名（如 {@code "PAID,SHIPPED"}）；空表示全部
     */
    PageResponse<OrderView> pageForCustomer(Long userId, int page, int size, int previewLimit,
                                            String status);

    OrderView detailForCustomer(Long userId, Long orderId);

    /** 订单物流；未发货时返回 null */
    LogisticsView logisticsForCustomer(Long userId, Long orderId);

    /**
     * agent 工具查询：按订单号或最近订单，**只返回当前用户自己的**。
     * 订单号不属于该用户时返回空列表 —— 不泄露订单是否存在。
     */
    List<OrderView> findForAgent(Long userId, String orderNo, String status, int limit);

    /** 模拟支付 */
    OrderView pay(Long userId, Long orderId);

    /** 取消订单（会回滚库存） */
    OrderView cancel(Long userId, Long orderId);

    OrderView confirmReceipt(Long userId, Long orderId);

    /** 申请退款：待发货 / 待收货 / 已完成 的订单可申请 */
    OrderView applyRefund(Long userId, Long orderId);

    // ── 管理端 ──

    /** 旧签名兼容 */
    default PageResponse<Order> pageForAdmin(int page, int size, String status) {
        return pageForAdmin(page, size, status, null);
    }

    /** 平台可按商户筛选 */
    PageResponse<Order> pageForAdmin(int page, int size, String status, Long merchantId);

    OrderView detailForAdmin(Long orderId);

    OrderView ship(Long orderId, ShipRequest request);

    /** 同意退款：状态转已退款、回滚库存、支付流水置为已退款 */
    OrderView approveRefund(Long orderId);

    /** 驳回退款：回到申请前的状态，库存不动 */
    OrderView rejectRefund(Long orderId);

    // ── 商家端 ──

    PageResponse<Order> pageForMerchant(Long merchantId, int page, int size, String status);

    OrderView detailForMerchant(Long merchantId, Long orderId);

    OrderView shipForMerchant(Long merchantId, Long orderId, ShipRequest request);

    OrderView approveRefundForMerchant(Long merchantId, Long orderId);

    OrderView rejectRefundForMerchant(Long merchantId, Long orderId);

    /**
     * 取消超时未支付的订单并回滚库存。
     * @param minutes 超过多少分钟算超时；≤0 用默认值
     * @return 实际取消的订单数
     */
    int cancelExpired(int minutes);
}
