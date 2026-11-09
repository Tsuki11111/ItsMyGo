package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentActionProperties;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.AgentActionStatus;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** agent 动作的公共快照、视图与 JSON 辅助。 */
@Slf4j
@Component
public class AgentActionSupport {

    private final AgentActionMapper actionMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final AgentActionProperties properties;

    public AgentActionSupport(AgentActionMapper actionMapper,
                              OrderMapper orderMapper,
                              ObjectMapper objectMapper,
                              AgentActionProperties properties) {
        this.actionMapper = actionMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AgentAction newAction(long userId, String sessionId,
                                 com.tsuk1.itsmygo.entity.AgentActionType type,
                                 Map<String, Object> payload, Map<String, Object> summary,
                                 Long targetId) {
        AgentAction action = new AgentAction();
        action.setActionNo(actionNo());
        action.setUserId(userId);
        action.setSessionId(sessionId == null ? "" : sessionId);
        action.setTargetId(targetId);
        action.setType(type.name());
        action.setStatus(AgentActionStatus.PENDING_CONFIRM.name());
        action.setPayloadJson(json(payload));
        action.setSummaryJson(json(summary));
        action.setErrorMessage("");
        action.setExpiresAt(LocalDateTime.now().plusSeconds(properties.expirySeconds()));
        return action;
    }

    public AgentAction requireOwned(long userId, String actionNo) {
        AgentAction action = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getActionNo, actionNo)
                .eq(AgentAction::getUserId, userId));
        if (action == null) {
            throw new BizException(ResultCode.NOT_FOUND, "动作不存在");
        }
        return action;
    }

    public AgentActionView toView(AgentAction action) {
        return toView(action, Map.of());
    }

    public List<AgentActionView> toViews(List<AgentAction> actions) {
        Set<Long> orderIds = new HashSet<>();
        for (AgentAction action : actions) {
            collectOrderIds(action, orderIds);
        }
        Map<Long, Order> currentOrders = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectByIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return actions.stream().map(action -> toView(action, currentOrders)).toList();
    }

    private AgentActionView toView(AgentAction action, Map<Long, Order> currentOrders) {
        Object result = readJson(action.getResultJson());
        enrichResult(action, result, currentOrders);
        return new AgentActionView(
                action.getActionNo(),
                action.getType(),
                action.getStatus(),
                readJson(action.getSummaryJson()),
                result,
                nullToEmpty(action.getErrorMessage()),
                action.getExpiresAt(),
                action.getCreatedAt());
    }

    private void collectOrderIds(AgentAction action, Set<Long> orderIds) {
        if (!AgentActionStatus.SUCCEEDED.name().equals(action.getStatus())) {
            return;
        }
        Object result = readJson(action.getResultJson());
        if (!(result instanceof Map<?, ?> map)) {
            return;
        }
        if (com.tsuk1.itsmygo.entity.AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
            if (map.get("orders") instanceof List<?> orders) {
                for (Object item : orders) {
                    if (item instanceof Map<?, ?> order) {
                        Long id = toLong(order.get("id"));
                        if (id != null) {
                            orderIds.add(id);
                        }
                    }
                }
            }
        } else if (com.tsuk1.itsmygo.entity.AgentActionType.APPLY_REFUND.name().equals(action.getType())
                || com.tsuk1.itsmygo.entity.AgentActionType.URGE_SHIPMENT.name().equals(action.getType())
                || com.tsuk1.itsmygo.entity.AgentActionType.URGE_REFUND.name().equals(action.getType())) {
            Long id = toLong(map.get("orderId"));
            if (id != null) {
                orderIds.add(id);
            }
        }
    }

    /** 动作结果是执行时的快照；读取列表时用订单当前状态覆盖，支付 / 发货后卡片才不会停在旧状态。 */
    private void enrichResult(AgentAction action, Object result, Map<Long, Order> currentOrders) {
        if (!(result instanceof Map<?, ?> raw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        if (com.tsuk1.itsmygo.entity.AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
            if (map.get("orders") instanceof List<?> orders) {
                for (Object item : orders) {
                    if (item instanceof Map<?, ?> rawOrder) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> order = (Map<String, Object>) rawOrder;
                        applyOrderStatus(order, currentOrders.get(toLong(order.get("id"))));
                    }
                }
            }
        } else if (com.tsuk1.itsmygo.entity.AgentActionType.APPLY_REFUND.name().equals(action.getType())
                || com.tsuk1.itsmygo.entity.AgentActionType.URGE_SHIPMENT.name().equals(action.getType())
                || com.tsuk1.itsmygo.entity.AgentActionType.URGE_REFUND.name().equals(action.getType())) {
            applyOrderStatus(map, currentOrders.get(toLong(map.get("orderId"))));
        }
    }

    private void applyOrderStatus(Map<String, Object> target, Order order) {
        if (order == null) {
            return;
        }
        target.put("status", order.getStatus());
        target.put("statusLabel", statusLabel(order.getStatus()));
    }

    private String statusLabel(String status) {
        try {
            return OrderStatus.valueOf(status).label();
        } catch (Exception e) {
            return status;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("动作快照损坏: {}", json, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "动作快照损坏，请重新生成");
        }
    }

    public Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "动作快照生成失败");
        }
    }

    public OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单状态异常");
        }
    }

    public String actionNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String specSuffix(ProductSku sku) {
        return StringUtils.hasText(sku.getSpecText()) ? "（" + sku.getSpecText() + "）" : "";
    }

    public String fullAddress(com.tsuk1.itsmygo.entity.Address address) {
        return nullToEmpty(address.getProvince()) + nullToEmpty(address.getCity())
                + nullToEmpty(address.getDistrict()) + nullToEmpty(address.getDetail());
    }

    public String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public boolean productIdEquals(Product product, ProductSku sku) {
        return sku.getProductId() != null && Objects.equals(product.getId(), sku.getProductId());
    }

    public boolean userIdEquals(long userId, Long ownerId) {
        return ownerId != null && ownerId == userId;
    }

    public void requireEnabled() {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "智能下单 / 退款暂未开放");
        }
    }
}
