package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.AgentActionStatus;
import com.tsuk1.itsmygo.entity.AgentActionType;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import com.tsuk1.itsmygo.service.OrderService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 退款草稿的准备与执行。 */
@Component
public class AgentRefundActionService {

    private final AgentActionMapper actionMapper;
    private final OrderService orderService;
    private final AgentActionSupport support;

    public AgentRefundActionService(AgentActionMapper actionMapper,
                                    OrderService orderService,
                                    AgentActionSupport support) {
        this.actionMapper = actionMapper;
        this.orderService = orderService;
        this.support = support;
    }

    public AgentActionView prepare(long userId, String sessionId, AgentRefundDraftRequest request) {
        support.requireEnabled();
        if (request == null || !StringUtils.hasText(request.orderNo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要退款的订单号");
        }
        String orderNo = request.orderNo().trim();
        String reason = request.reason() == null ? "" : request.reason().trim();
        List<OrderView> found = orderService.findForAgent(userId, orderNo, null, 1);
        if (found.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderView view = found.get(0);
        Order order = view.order();
        OrderStatus status = support.parseStatus(order.getStatus());
        if (!status.refundable()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "当前订单状态（" + view.statusLabel() + "）不可申请退款");
        }

        // 幂等：同一订单已有未过期的待确认退款草稿时，直接返回那一张卡；
        // 本次表单带了新原因则更新草稿，确认时以最新原因为准。
        AgentAction existing = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getType, AgentActionType.APPLY_REFUND.name())
                .eq(AgentAction::getTargetId, order.getId())
                .eq(AgentAction::getStatus, AgentActionStatus.PENDING_CONFIRM.name())
                .orderByDesc(AgentAction::getId)
                .last("LIMIT 1"));
        if (existing != null && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
            if (StringUtils.hasText(reason)) {
                updateReason(existing, reason);
            }
            return support.toView(existing);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderNo", order.getOrderNo());
        summary.put("merchantName", support.nullToEmpty(order.getMerchantName()));
        summary.put("status", order.getStatus());
        summary.put("statusLabel", view.statusLabel());
        summary.put("totalAmount", support.money(order.getTotalAmount()));
        summary.put("refundReason", StringUtils.hasText(reason) ? reason : "未填写");
        summary.put("items", view.items() == null ? List.of()
                : view.items().stream().map(this::itemView).toList());
        summary.put("note", "确认后提交退款申请，等待商家处理；库存与优惠券在商家同意退款后回滚。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("reason", reason);

        AgentAction action = support.newAction(userId, sessionId, AgentActionType.APPLY_REFUND,
                payload, summary, order.getId());
        actionMapper.insert(action);
        return support.toView(action);
    }

    public Object execute(long userId, AgentAction action) {
        String payloadJson = action.getPayloadJson();
        var payload = support.readTree(payloadJson);
        long orderId = payload.path("orderId").asLong();
        String reason = payload.path("reason").asText("");
        // 老草稿没有 reason（协议上线前生成），保持原来的通用文案
        OrderView view = orderService.applyRefund(userId, orderId,
                StringUtils.hasText(reason) ? reason : "通过买小购申请退款");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", view.order().getId());
        result.put("orderNo", view.order().getOrderNo());
        result.put("status", view.order().getStatus());
        result.put("statusLabel", view.statusLabel());
        result.put("note", "退款申请已提交，等待商家处理。");
        return result;
    }

    /** 待确认退款草稿已经存在时，用本次表单提交的原因覆盖旧快照。 */
    private void updateReason(AgentAction action, String reason) {
        if (support.readJson(action.getPayloadJson()) instanceof Map<?, ?> rawPayload) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) rawPayload;
            payload.put("reason", reason);
            action.setPayloadJson(support.json(payload));
        }
        if (support.readJson(action.getSummaryJson()) instanceof Map<?, ?> rawSummary) {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) rawSummary;
            summary.put("refundReason", reason);
            action.setSummaryJson(support.json(summary));
        }
        actionMapper.updateById(action);
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
}
