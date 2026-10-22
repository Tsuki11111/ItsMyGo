package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ShipRequest;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 管理端订单：查看全部订单、发货 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private static final DateTimeFormatter EXPORT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Result<PageResponse<Order>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(orderService.pageForAdmin(
                page, size, status, merchantId, keyword, dateFrom, dateTo));
    }

    /** 导出当前筛选条件下的订单 CSV（最多 5000 条） */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletResponse response) throws IOException {
        List<Order> orders = orderService.listForAdminExport(
                status, merchantId, keyword, dateFrom, dateTo);

        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write(0xFEFF);
        writer.println("订单号,下单时间,支付时间,状态,店铺,用户ID,收货人,电话,金额,优惠,"
                + "物流单号,退款原因,退款申请时间,退款处理时间");
        for (Order order : orders) {
            writer.println(String.join(",",
                    csv(order.getOrderNo()),
                    csv(format(order.getCreatedAt())),
                    csv(format(order.getPaidAt())),
                    csv(statusLabel(order.getStatus())),
                    csv(order.getMerchantName()),
                    csv(String.valueOf(order.getUserId())),
                    csv(order.getReceiver()),
                    csv(order.getPhone()),
                    csv(money(order.getTotalAmount())),
                    csv(money(order.getDiscountAmount())),
                    csv(order.getTrackingNo()),
                    csv(order.getRefundReason()),
                    csv(format(order.getRefundAppliedAt())),
                    csv(format(order.getRefundHandledAt()))));
        }
        writer.flush();
    }

    @GetMapping("/{id}")
    public Result<OrderView> detail(@PathVariable Long id) {
        return Result.ok(orderService.detailForAdmin(id));
    }

    @PostMapping("/{id}/ship")
    public Result<OrderView> ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request) {
        return Result.ok(orderService.ship(id, request));
    }

    /** 同意退款：状态转已退款、回滚库存、支付流水同步 */
    @PostMapping("/{id}/refund/approve")
    public Result<OrderView> approveRefund(@PathVariable Long id) {
        return Result.ok(orderService.approveRefund(id));
    }

    /** 驳回退款：回到申请前的状态 */
    @PostMapping("/{id}/refund/reject")
    public Result<OrderView> rejectRefund(@PathVariable Long id) {
        return Result.ok(orderService.rejectRefund(id));
    }

    /**
     * 取消超时未支付的订单。
     * 现在是手动触发（自己测试、或将来挂定时任务时调它）；
     * 真要自动化就加个 @Scheduled，注意多实例下得配分布式锁。
     */
    @PostMapping("/cancel-expired")
    public Result<Integer> cancelExpired(@RequestParam(defaultValue = "30") int minutes) {
        return Result.ok(orderService.cancelExpired(minutes));
    }

    private String csv(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"")
                || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.valueOf(status).label();
        } catch (Exception e) {
            return status == null ? "" : status;
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String format(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(EXPORT_TIME);
    }
}
