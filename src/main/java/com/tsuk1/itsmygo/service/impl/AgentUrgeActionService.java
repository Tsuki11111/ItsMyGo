package com.tsuk1.itsmygo.service.impl;

import agent.v1.Event;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeRefundRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeShipmentRequest;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.AgentActionStatus;
import com.tsuk1.itsmygo.entity.AgentActionType;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.service.ChatService;
import com.tsuk1.itsmygo.service.NotificationService;
import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 催发货 / 催退款草稿的准备、确认与发送。 */
@Slf4j
@Component
public class AgentUrgeActionService {

    private static final String URGE_SEND_LOCK_PREFIX = "its-my-go:agent:urge-send:";
    private static final String URGE_REFUND_SEND_LOCK_PREFIX =
            "its-my-go:agent:urge-refund-send:";
    private static final int URGE_COOLDOWN_MINUTES = 10;

    private final AgentActionMapper actionMapper;
    private final OrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final OrderService orderService;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final AgentGateway agentGateway;
    private final AgentToolTicketService ticketService;
    private final StringRedisTemplate redis;
    private final AgentActionSupport support;

    public AgentUrgeActionService(AgentActionMapper actionMapper,
                                  OrderMapper orderMapper,
                                  MerchantMapper merchantMapper,
                                  OrderService orderService,
                                  ChatService chatService,
                                  NotificationService notificationService,
                                  AgentGateway agentGateway,
                                  AgentToolTicketService ticketService,
                                  StringRedisTemplate redis,
                                  AgentActionSupport support) {
        this.actionMapper = actionMapper;
        this.orderMapper = orderMapper;
        this.merchantMapper = merchantMapper;
        this.orderService = orderService;
        this.chatService = chatService;
        this.notificationService = notificationService;
        this.agentGateway = agentGateway;
        this.ticketService = ticketService;
        this.redis = redis;
        this.support = support;
    }

    public AgentActionView prepareShipment(long userId, String sessionId,
                                           AgentUrgeShipmentRequest request) {
        support.requireEnabled();
        if (request == null || !StringUtils.hasText(request.orderNo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要催发货的订单号");
        }
        String orderNo = request.orderNo().trim();
        List<OrderView> found = orderService.findForAgent(userId, orderNo, null, 1);
        if (found.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderView view = found.get(0);
        Order order = view.order();
        if (order.getMerchantId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单缺少店铺信息，暂时无法催发货");
        }
        OrderStatus status = support.parseStatus(order.getStatus());
        if (order.getShippedAt() != null || status == OrderStatus.SHIPPED) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单已是待收货状态，无需催单；物流状态可在订单详情页查看");
        }
        if (status == OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单还未支付，请先完成支付后再催发货");
        }
        if (status != OrderStatus.PAID) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "当前订单状态（" + status.label() + "）不可催发货");
        }

        LocalDateTime now = LocalDateTime.now();
        AgentAction latest = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getType, AgentActionType.URGE_SHIPMENT.name())
                .eq(AgentAction::getTargetId, order.getId())
                .in(AgentAction::getStatus,
                        AgentActionStatus.PENDING_CONFIRM.name(),
                        AgentActionStatus.SUCCEEDED.name())
                .orderByDesc(AgentAction::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            if (AgentActionStatus.PENDING_CONFIRM.name().equals(latest.getStatus())
                    && latest.getExpiresAt() != null && latest.getExpiresAt().isAfter(now)) {
                return support.toView(latest);
            }
            LocalDateTime sentAt = latest.getExecutedAt() == null
                    ? latest.getUpdatedAt() : latest.getExecutedAt();
            if (AgentActionStatus.SUCCEEDED.name().equals(latest.getStatus())
                    && sentAt != null
                    && sentAt.isAfter(now.minusMinutes(URGE_COOLDOWN_MINUTES))) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "刚刚已经催过发货了，请 " + URGE_COOLDOWN_MINUTES + " 分钟后再试");
            }
        }

        List<OrderItem> items = view.items() == null ? List.of() : view.items();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderNo", order.getOrderNo());
        summary.put("merchantName", support.nullToEmpty(order.getMerchantName()));
        summary.put("status", order.getStatus());
        summary.put("statusLabel", view.statusLabel());
        summary.put("totalAmount", support.money(order.getTotalAmount()));
        summary.put("itemCount", items.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum());
        summary.put("items", items.stream().map(this::itemView).toList());
        summary.put("note", "确认后买小购会通过商家站内通知提醒尽快发货；"
                + "平台自营店通知平台管理员。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());

        AgentAction action = support.newAction(userId, sessionId, AgentActionType.URGE_SHIPMENT,
                payload, summary, order.getId());
        actionMapper.insert(action);
        return support.toView(action);
    }

    public AgentActionView prepareRefund(long userId, String sessionId,
                                         AgentUrgeRefundRequest request) {
        support.requireEnabled();
        if (request == null || !StringUtils.hasText(request.orderNo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要催退款的订单号");
        }
        String orderNo = request.orderNo().trim();
        List<OrderView> found = orderService.findForAgent(userId, orderNo, null, 1);
        if (found.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderView view = found.get(0);
        Order order = view.order();
        if (order.getMerchantId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单缺少店铺信息，暂时无法催退款");
        }
        OrderStatus status = support.parseStatus(order.getStatus());
        if (status == OrderStatus.REFUNDED) {
            throw new BizException(ResultCode.BAD_REQUEST, "商家已处理退款，无需再催");
        }
        if (status != OrderStatus.REFUNDING) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "当前订单状态（" + status.label() + "）不可催退款");
        }

        LocalDateTime now = LocalDateTime.now();
        AgentAction latest = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getType, AgentActionType.URGE_REFUND.name())
                .eq(AgentAction::getTargetId, order.getId())
                .in(AgentAction::getStatus,
                        AgentActionStatus.PENDING_CONFIRM.name(),
                        AgentActionStatus.SUCCEEDED.name())
                .orderByDesc(AgentAction::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            if (AgentActionStatus.PENDING_CONFIRM.name().equals(latest.getStatus())
                    && latest.getExpiresAt() != null && latest.getExpiresAt().isAfter(now)) {
                return support.toView(latest);
            }
            LocalDateTime sentAt = latest.getExecutedAt() == null
                    ? latest.getUpdatedAt() : latest.getExecutedAt();
            if (AgentActionStatus.SUCCEEDED.name().equals(latest.getStatus())
                    && sentAt != null
                    && sentAt.isAfter(now.minusMinutes(URGE_COOLDOWN_MINUTES))) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "刚刚已经催过退款了，请 " + URGE_COOLDOWN_MINUTES + " 分钟后再试");
            }
        }

        List<OrderItem> items = view.items() == null ? List.of() : view.items();
        LocalDateTime appliedAt = order.getRefundAppliedAt() == null
                ? order.getUpdatedAt() : order.getRefundAppliedAt();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderNo", order.getOrderNo());
        summary.put("merchantName", support.nullToEmpty(order.getMerchantName()));
        summary.put("status", order.getStatus());
        summary.put("statusLabel", view.statusLabel());
        summary.put("totalAmount", support.money(order.getTotalAmount()));
        summary.put("refundReason", StringUtils.hasText(order.getRefundReason())
                ? order.getRefundReason() : "未填写");
        summary.put("refundAppliedAt", appliedAt == null ? "" : appliedAt.toString());
        summary.put("itemCount", items.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum());
        summary.put("items", items.stream().map(this::itemView).toList());
        summary.put("note", "确认后买小购会以平台客服身份给商家发送一条催退款消息；"
                + "平台自营店由平台客服接收。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());

        AgentAction action = support.newAction(userId, sessionId, AgentActionType.URGE_REFUND,
                payload, summary, order.getId());
        actionMapper.insert(action);
        return support.toView(action);
    }

    public AgentActionView executeShipment(long userId, String sessionId, String actionNo) {
        return executeUrgeAction(userId, sessionId, actionNo, UrgeKind.SHIPMENT);
    }

    public AgentActionView executeRefund(long userId, String sessionId, String actionNo) {
        return executeUrgeAction(userId, sessionId, actionNo, UrgeKind.REFUND);
    }

    private AgentActionView executeUrgeAction(long userId, String sessionId, String actionNo,
                                               UrgeKind kind) {
        support.requireEnabled();
        AgentAction action = support.requireOwned(userId, actionNo);
        if (!kind.type.name().equals(action.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "动作类型不匹配");
        }
        if (StringUtils.hasText(sessionId) && StringUtils.hasText(action.getSessionId())
                && !sessionId.equals(action.getSessionId())) {
            throw new BizException(ResultCode.NOT_FOUND, "动作不存在");
        }
        if (AgentActionStatus.SUCCEEDED.name().equals(action.getStatus())) {
            return support.toView(action);
        }
        if (!AgentActionStatus.EXECUTING.name().equals(action.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "动作不在执行状态");
        }

        String lockKey = kind.lockPrefix + actionNo;
        Boolean locked = redis.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            AgentAction fresh = support.requireOwned(userId, actionNo);
            if (AgentActionStatus.SUCCEEDED.name().equals(fresh.getStatus())) {
                return support.toView(fresh);
            }
            throw new BizException(ResultCode.BAD_REQUEST,
                    kind.label + "正在执行，请稍后");
        }
        try {
            Map<String, Object> result = sendUrgeMessage(userId, action, kind);
            if (actionMapper.finishExecution(actionNo, userId, support.json(result)) == 0) {
                AgentAction fresh = support.requireOwned(userId, actionNo);
                if (AgentActionStatus.SUCCEEDED.name().equals(fresh.getStatus())) {
                    return support.toView(fresh);
                }
                throw new BizException(ResultCode.BAD_REQUEST, "动作状态已变化，请刷新后重试");
            }
            return support.toView(support.requireOwned(userId, actionNo));
        } catch (BizException e) {
            actionMapper.failExecution(actionNo, userId, support.truncate(e.getMessage(), 255));
            throw e;
        } catch (Exception e) {
            log.error("{}执行失败: {}", kind.label, actionNo, e);
            actionMapper.failExecution(actionNo, userId,
                    kind.label + "执行失败，请稍后重试");
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    kind.label + "执行失败，请稍后重试");
        } finally {
            redis.delete(lockKey);
        }
    }

    private Map<String, Object> sendUrgeMessage(long userId, AgentAction action, UrgeKind kind) {
        JsonNode payload = support.readTree(action.getPayloadJson());
        long orderId = payload.path("orderId").asLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !support.userIdEquals(userId, order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getMerchantId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "订单缺少店铺信息，暂时无法" + kind.label);
        }
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        String orderNo = support.nullToEmpty(order.getOrderNo());
        String content;
        OrderStatus status = support.parseStatus(order.getStatus());
        Map<String, Object> result = new LinkedHashMap<>();

        if (kind == UrgeKind.SHIPMENT) {
            if (order.getShippedAt() != null || status != OrderStatus.PAID) {
                String label = order.getShippedAt() != null
                        ? OrderStatus.SHIPPED.label() : status.label();
                throw new BizException(ResultCode.BAD_REQUEST,
                        "当前订单状态（" + label + "）不可催发货");
            }
            content = "【催发货】订单 " + orderNo
                    + " 已支付，当前待发货。买家希望尽快发货，麻烦您尽快安排；"
                    + "给您带来不便，敬请谅解。—— 买小购";
            // 催发货只发商家站内通知，不再写入顾客—商家会话，避免买家看到内部催办
            notificationService.notifyMerchant(order.getMerchantId(), "URGE_SHIPMENT",
                    "买家催发货", content,
                    "/merchant/orders?orderId=" + order.getId(),
                    "/admin/orders?orderId=" + order.getId(),
                    "urge-shipment:" + action.getActionNo());
            result.put("note", "已通过站内通知提醒商家尽快发货，请留意商家处理进度。");
        } else {
            if (status == OrderStatus.REFUNDED) {
                throw new BizException(ResultCode.BAD_REQUEST, "商家已处理退款，无需再催");
            }
            if (status != OrderStatus.REFUNDING) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "当前订单状态（" + status.label() + "）不可催退款");
            }
            String reason = StringUtils.hasText(order.getRefundReason())
                    ? order.getRefundReason() : "";
            String reasonPart = reason.isEmpty() ? "" : "（原因：" + reason + "）";
            content = "【催退款】订单 " + orderNo + " 的退款申请" + reasonPart
                    + "仍在处理中，当前状态：退款中。买家希望尽快审核处理，"
                    + "麻烦您尽快跟进；给您带来不便，敬请谅解。—— 买小购";
            LocalDateTime appliedAt = order.getRefundAppliedAt() == null
                    ? order.getUpdatedAt() : order.getRefundAppliedAt();
            result.put("refundReason", reason);
            result.put("refundAppliedAt", appliedAt == null ? "" : appliedAt.toString());
            result.put("note", "催退款消息已发送给商家，请留意商家处理进度。");
        }

        // 催退款维持原通道：以平台客服身份发到顾客—商家会话，方便商家在消息中心处理
        if (kind != UrgeKind.SHIPMENT) {
            ChatMessageView message = chatService.sendAgentText(
                    userId, order.getMerchantId(), content);
            result.put("messageId", message.id());
        }
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("merchantName", merchant == null ? "" : support.nullToEmpty(merchant.getName()));
        result.put("status", order.getStatus());
        result.put("statusLabel", status.label());
        return result;
    }

    public AgentActionView confirmShipment(long userId, AgentAction action) {
        return confirmUrgeAction(userId, action, UrgeKind.SHIPMENT);
    }

    public AgentActionView confirmRefund(long userId, AgentAction action) {
        return confirmUrgeAction(userId, action, UrgeKind.REFUND);
    }

    /**
     * 确认后不直接发消息：发 gRPC 内部事件触发 agent 图；
     * agent 调对应发送工具回调 Java，由 executeUrgeAction 校验并落库。
     */
    private AgentActionView confirmUrgeAction(long userId, AgentAction action, UrgeKind kind) {
        if (!agentGateway.configured() || !StringUtils.hasText(action.getSessionId())) {
            actionMapper.failExecution(action.getActionNo(), userId,
                    "客服智能体不可用，暂时无法" + kind.label);
            return support.toView(support.requireOwned(userId, action.getActionNo()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionNo", action.getActionNo());
        String ticket = ticketService.issue(userId, action.getSessionId());
        try {
            Iterator<Event> events = agentGateway.internalEvent(userId, action.getSessionId(),
                    kind.event, support.json(payload), ticket, Duration.ofSeconds(20));
            while (events.hasNext()) {
                Event event = events.next();
                if (event.hasError()) {
                    log.warn("{} agent 执行报错: {}", kind.label, event.getError().getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("{} agent 调用失败: {}", kind.label, action.getActionNo(), e);
            actionMapper.failExecution(action.getActionNo(), userId,
                    kind.label + "执行失败，请稍后重试");
        }
        AgentAction fresh = support.requireOwned(userId, action.getActionNo());
        if (AgentActionStatus.EXECUTING.name().equals(fresh.getStatus())) {
            actionMapper.failExecution(fresh.getActionNo(), userId,
                    kind.label + "执行超时，请重新发起");
            fresh = support.requireOwned(userId, action.getActionNo());
        }
        return support.toView(fresh);
    }

    private Map<String, Object> itemView(OrderItem item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("productName", support.nullToEmpty(item.getProductName()));
        view.put("specText", support.nullToEmpty(item.getSpecText()));
        view.put("quantity", item.getQuantity());
        view.put("unitPrice", support.money(item.getUnitPrice()));
        view.put("subtotal", support.money(item.getSubtotal()));
        view.put("image", support.nullToEmpty(item.getProductImage()));
        return view;
    }

    /** 两类催办的差异都收在这里，执行/确认/发送逻辑共用。 */
    private enum UrgeKind {
        SHIPMENT(AgentActionType.URGE_SHIPMENT, "urge-shipment-confirmed",
                URGE_SEND_LOCK_PREFIX, "催发货"),
        REFUND(AgentActionType.URGE_REFUND, "urge-refund-confirmed",
                URGE_REFUND_SEND_LOCK_PREFIX, "催退款");

        private final AgentActionType type;
        private final String event;
        private final String lockPrefix;
        private final String label;

        UrgeKind(AgentActionType type, String event, String lockPrefix, String label) {
            this.type = type;
            this.event = event;
            this.lockPrefix = lockPrefix;
            this.label = label;
        }
    }
}
