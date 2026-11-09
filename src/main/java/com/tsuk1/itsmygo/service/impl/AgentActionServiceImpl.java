package com.tsuk1.itsmygo.service.impl;

import agent.v1.Event;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentActionProperties;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CheckoutView;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeRefundRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeShipmentRequest;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.AgentActionStatus;
import com.tsuk1.itsmygo.entity.AgentActionType;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import com.tsuk1.itsmygo.service.AgentActionService;
import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待确认动作：prepare 只落快照，confirm 才调业务 Service。
 *
 * <p>三条不变量：
 * <ol>
 *   <li>确认接口只收 actionNo，业务参数全部来自服务端快照</li>
 *   <li>userId 来自登录态/票据，模型和浏览器都改不了</li>
 *   <li>CAS 状态流转，双击确认只有一个会执行</li>
 * </ol>
 *
 * <p>下单、退款、催办的参数准备与执行分别交给
 * {@link AgentOrderDraftBuilder}、{@link AgentRefundActionService}、{@link AgentUrgeActionService}，
 * 本类只保留动作状态机与入口编排。
 */
@Slf4j
@Service
public class AgentActionServiceImpl implements AgentActionService {

    private static final String TOKEN_PREFIX = "its-my-go:agent:action-token:";

    private final AgentActionMapper actionMapper;
    private final OrderService orderService;
    private final AgentOrderDraftBuilder orderDraftBuilder;
    private final AgentRefundActionService refundActionService;
    private final AgentUrgeActionService urgeActionService;
    private final AgentActionSupport support;
    private final AgentGateway agentGateway;
    private final AgentToolTicketService ticketService;
    private final StringRedisTemplate redis;
    private final AgentActionProperties properties;

    public AgentActionServiceImpl(AgentActionMapper actionMapper,
                                  OrderService orderService,
                                  AgentOrderDraftBuilder orderDraftBuilder,
                                  AgentRefundActionService refundActionService,
                                  AgentUrgeActionService urgeActionService,
                                  AgentActionSupport support,
                                  AgentGateway agentGateway,
                                  AgentToolTicketService ticketService,
                                  StringRedisTemplate redis,
                                  AgentActionProperties properties) {
        this.actionMapper = actionMapper;
        this.orderService = orderService;
        this.orderDraftBuilder = orderDraftBuilder;
        this.refundActionService = refundActionService;
        this.urgeActionService = urgeActionService;
        this.support = support;
        this.agentGateway = agentGateway;
        this.ticketService = ticketService;
        this.redis = redis;
        this.properties = properties;
    }

    // ── 准备 ──

    @Override
    @Transactional
    public AgentActionView prepareOrder(long userId, String sessionId,
                                        AgentOrderDraftRequest request) {
        support.requireEnabled();
        AgentOrderDraftBuilder.OrderDraft draft = orderDraftBuilder.build(userId, request);
        // 幂等：同一会话里对同一份订单内容重复点「让买小购下单」时，返回已有草稿卡片，
        // 不再重复生成（否则用户会看到多张一模一样的「订单待确认」）。
        String payloadJson = support.json(draft.payload());
        List<AgentAction> pendingOrders = actionMapper.selectList(
                Wrappers.<AgentAction>lambdaQuery()
                        .eq(AgentAction::getUserId, userId)
                        .eq(AgentAction::getSessionId, sessionId)
                        .eq(AgentAction::getType, AgentActionType.CREATE_ORDER.name())
                        .eq(AgentAction::getStatus, AgentActionStatus.PENDING_CONFIRM.name())
                        .gt(AgentAction::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(AgentAction::getId));
        for (AgentAction existing : pendingOrders) {
            if (payloadJson.equals(existing.getPayloadJson())) {
                log.info("重复的订单草稿，返回已有卡片：{}", existing.getActionNo());
                return support.toView(existing);
            }
        }
        // 同一会话只保留最新一张待确认下单卡：agent 换规格/换商品重新 prepare 时，
        // 旧卡若继续 PENDING，前端按 FIFO 会先展示旧卡，用户可能确认错误规格。
        for (AgentAction existing : pendingOrders) {
            int cancelled = actionMapper.casStatus(existing.getActionNo(), userId,
                    AgentActionStatus.PENDING_CONFIRM.name(), AgentActionStatus.CANCELLED.name());
            if (cancelled > 0) {
                log.info("新下单草稿替换旧草稿：{}", existing.getActionNo());
            }
        }

        AgentAction action = support.newAction(userId, sessionId, AgentActionType.CREATE_ORDER,
                draft.payload(), draft.summary(), null);
        String token = orderService.issueToken(userId);
        redis.opsForValue().set(TOKEN_PREFIX + action.getActionNo(), token,
                Duration.ofSeconds(properties.expirySeconds()));
        actionMapper.insert(action);
        return support.toView(action);
    }

    @Override
    public AgentActionView updateOrderDraft(long userId, String actionNo,
                                            AgentOrderDraftRequest request) {
        support.requireEnabled();
        AgentAction action = support.requireOwned(userId, actionNo);
        if (!AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有下单草稿支持修改");
        }
        if (!AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该草稿已确认或取消，不能再修改");
        }
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.BAD_REQUEST, "草稿已过期，请重新发起下单");
        }
        // 复用 prepare 的校验与重新计价：用户只改草稿，不直接动订单
        AgentOrderDraftBuilder.OrderDraft draft = orderDraftBuilder.build(userId, request);
        action.setPayloadJson(support.json(draft.payload()));
        action.setSummaryJson(support.json(draft.summary()));
        actionMapper.updateById(action);
        log.info("下单草稿已修改：{}", actionNo);
        return support.toView(action);
    }

    @Override
    @Transactional
    public AgentActionView reprepareOrder(long userId, String actionNo) {
        support.requireEnabled();
        AgentAction action = support.requireOwned(userId, actionNo);
        if (!AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有下单草稿支持重新下单");
        }
        if (!AgentActionStatus.CANCELLED.name().equals(action.getStatus())
                && !AgentActionStatus.EXPIRED.name().equals(action.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有已取消或已过期的草稿可以重新下单");
        }
        JsonNode payload = support.readTree(action.getPayloadJson());
        long addressId = payload.path("addressId").asLong();
        List<AgentOrderDraftRequest.Item> items = new ArrayList<>();
        for (JsonNode node : payload.path("items")) {
            items.add(new AgentOrderDraftRequest.Item(
                    node.path("productId").asLong(),
                    node.path("skuId").asLong(),
                    node.path("quantity").asInt()));
        }
        if (addressId <= 0 || items.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "原草稿内容已失效，请重新告诉买小购准备");
        }
        // 复用 prepare：重新校验库存/地址并生成新的待确认草稿
        return prepareOrder(userId, action.getSessionId(),
                new AgentOrderDraftRequest(addressId, items));
    }

    @Override
    public AgentActionView prepareRefund(long userId, String sessionId,
                                         AgentRefundDraftRequest request) {
        return refundActionService.prepare(userId, sessionId, request);
    }

    @Override
    public AgentActionView prepareUrgeShipment(long userId, String sessionId,
                                               AgentUrgeShipmentRequest request) {
        return urgeActionService.prepareShipment(userId, sessionId, request);
    }

    @Override
    public AgentActionView prepareUrgeRefund(long userId, String sessionId,
                                             AgentUrgeRefundRequest request) {
        return urgeActionService.prepareRefund(userId, sessionId, request);
    }

    // ── 确认 / 取消 / 列表 ──

    @Override
    public AgentActionView confirm(long userId, String actionNo) {
        support.requireEnabled();
        AgentAction action = support.requireOwned(userId, actionNo);
        if (AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())
                && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            actionMapper.casStatus(actionNo, userId,
                    AgentActionStatus.PENDING_CONFIRM.name(), AgentActionStatus.EXPIRED.name());
            action.setStatus(AgentActionStatus.EXPIRED.name());
            return support.toView(action);
        }
        if (AgentActionStatus.EXECUTING.name().equals(action.getStatus())
                && action.getUpdatedAt() != null
                && action.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            // 进程在 CAS 之后、执行完成之前崩了：留一个可恢复的失败态，避免永久卡在 EXECUTING
            action.setStatus(AgentActionStatus.FAILED.name());
            action.setErrorMessage("执行中断，请重新发起");
            action.setExecutedAt(LocalDateTime.now());
            actionMapper.updateById(action);
            return support.toView(action);
        }
        if (!AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())) {
            // 已确认 / 已失败 / 已取消 / 已过期：幂等返回当前状态，不重复执行
            return support.toView(action);
        }

        int started = actionMapper.startExecution(actionNo, userId);
        if (started == 0) {
            return support.toView(support.requireOwned(userId, actionNo));
        }
        action.setStatus(AgentActionStatus.EXECUTING.name());
        if (AgentActionType.URGE_SHIPMENT.name().equals(action.getType())) {
            // 催发货不在这里直接发：触发 agent 图，由 agent 调用发送工具回调 Java
            return urgeActionService.confirmShipment(userId, action);
        }
        if (AgentActionType.URGE_REFUND.name().equals(action.getType())) {
            // 催退款同理，由 agent 图调用发送工具
            return urgeActionService.confirmRefund(userId, action);
        }
        try {
            Object result = switch (AgentActionType.valueOf(action.getType())) {
                case CREATE_ORDER -> executeOrder(userId, action);
                case APPLY_REFUND -> refundActionService.execute(userId, action);
                case URGE_SHIPMENT -> throw new BizException(
                        ResultCode.INTERNAL_ERROR, "催发货由 agent 工具执行");
                case URGE_REFUND -> throw new BizException(
                        ResultCode.INTERNAL_ERROR, "催退款由 agent 工具执行");
            };
            action.setStatus(AgentActionStatus.SUCCEEDED.name());
            action.setResultJson(support.json(result));
            action.setErrorMessage("");
            action.setExecutedAt(LocalDateTime.now());
            actionMapper.updateById(action);
            if (AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
                notifyOrderConfirmed(userId, action, result);
            }
            return support.toView(action);
        } catch (BizException e) {
            markFailed(action, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("agent 动作执行失败: {}", actionNo, e);
            markFailed(action, "执行失败，请稍后重试");
            throw new BizException(ResultCode.INTERNAL_ERROR, "执行失败，请稍后重试");
        }
    }

    @Override
    public AgentActionView cancel(long userId, String actionNo) {
        support.requireEnabled();
        support.requireOwned(userId, actionNo);
        actionMapper.casStatus(actionNo, userId,
                AgentActionStatus.PENDING_CONFIRM.name(), AgentActionStatus.CANCELLED.name());
        return support.toView(support.requireOwned(userId, actionNo));
    }

    @Override
    public List<AgentActionView> list(long userId, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        List<AgentAction> actions = actionMapper.selectList(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getSessionId, sessionId)
                .orderByAsc(AgentAction::getId));
        LocalDateTime now = LocalDateTime.now();
        for (AgentAction action : actions) {
            if (AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())
                    && action.getExpiresAt().isBefore(now)) {
                actionMapper.casStatus(action.getActionNo(), userId,
                        AgentActionStatus.PENDING_CONFIRM.name(),
                        AgentActionStatus.EXPIRED.name());
                action.setStatus(AgentActionStatus.EXPIRED.name());
            }
        }
        return support.toViews(actions);
    }

    // ── 执行 ──

    private Object executeOrder(long userId, AgentAction action) {
        String token = redis.opsForValue().getAndDelete(TOKEN_PREFIX + action.getActionNo());
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.BAD_REQUEST, "草稿已过期，请重新生成");
        }
        JsonNode payload = support.readTree(action.getPayloadJson());
        long addressId = payload.path("addressId").asLong();
        List<CreateOrderRequest.BuyNowItem> items = new ArrayList<>();
        for (JsonNode node : payload.path("items")) {
            items.add(new CreateOrderRequest.BuyNowItem(
                    node.path("productId").asLong(),
                    node.path("skuId").asLong(),
                    node.path("quantity").asInt()));
        }
        CheckoutView checkout = orderService.create(userId,
                new CreateOrderRequest(addressId, token, items, List.of()));

        List<Map<String, Object>> orders = checkout.orders().stream().map(view -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", view.order().getId());
            item.put("orderNo", view.order().getOrderNo());
            item.put("status", view.order().getStatus());
            item.put("statusLabel", view.statusLabel());
            item.put("totalAmount", support.money(view.order().getTotalAmount()));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders);
        result.put("note", "订单已生成，请到订单详情页完成支付。");
        return result;
    }

    @Override
    public AgentActionView executeUrgeShipment(long userId, String sessionId, String actionNo) {
        return urgeActionService.executeShipment(userId, sessionId, actionNo);
    }

    @Override
    public AgentActionView executeUrgeRefund(long userId, String sessionId, String actionNo) {
        return urgeActionService.executeRefund(userId, sessionId, actionNo);
    }

    /**
     * 下单成功后补一条买小购回复：订单已经创建成功，通知失败只记日志，
     * 不能让用户误以为下单失败；动作卡片仍会展示订单结果。
     */
    private void notifyOrderConfirmed(long userId, AgentAction action, Object result) {
        if (!agentGateway.configured() || !StringUtils.hasText(action.getSessionId())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionNo", action.getActionNo());
        if (result instanceof Map<?, ?> map) {
            payload.put("orders", map.get("orders"));
            payload.put("note", map.get("note"));
        }
        try {
            String ticket = ticketService.issue(userId, action.getSessionId());
            Iterator<Event> events = agentGateway.internalEvent(userId, action.getSessionId(),
                    "order-confirmed", support.json(payload), ticket, Duration.ofSeconds(5));
            while (events.hasNext()) {
                Event event = events.next();
                if (event.hasError()) {
                    log.warn("下单确认消息生成失败: {}", event.getError().getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("下单确认消息生成失败: {}", action.getActionNo(), e);
        }
    }

    // ── 辅助 ──

    private void markFailed(AgentAction action, String message) {
        action.setStatus(AgentActionStatus.FAILED.name());
        action.setErrorMessage(support.truncate(message, 255));
        action.setExecutedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }
}
