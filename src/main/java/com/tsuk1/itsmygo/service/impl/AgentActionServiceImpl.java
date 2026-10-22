package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import agent.v1.Event;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentActionProperties;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CheckoutView;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.dto.ChatMessageView;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentUrgeShipmentRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.AgentAction;
import com.tsuk1.itsmygo.entity.AgentActionStatus;
import com.tsuk1.itsmygo.entity.AgentActionType;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.OrderStatus;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.AgentActionMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.service.AgentActionService;
import com.tsuk1.itsmygo.service.ChatService;
import com.tsuk1.itsmygo.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 待确认动作：prepare 只落快照，confirm 才调业务 Service。
 *
 * <p>三条不变量：
 * <ol>
 *   <li>确认接口只收 actionNo，业务参数全部来自服务端快照</li>
 *   <li>userId 来自登录态/票据，模型和浏览器都改不了</li>
 *   <li>CAS 状态流转，双击确认只有一个会执行</li>
 * </ol>
 */
@Slf4j
@Service
public class AgentActionServiceImpl implements AgentActionService {

    private static final String TOKEN_PREFIX = "its-my-go:agent:action-token:";
    private static final String URGE_SEND_LOCK_PREFIX = "its-my-go:agent:urge-send:";
    private static final int URGE_COOLDOWN_MINUTES = 10;

    private final AgentActionMapper actionMapper;
    private final AddressMapper addressMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final MerchantMapper merchantMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ChatService chatService;
    private final AgentGateway agentGateway;
    private final AgentToolTicketService ticketService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AgentActionProperties properties;

    public AgentActionServiceImpl(AgentActionMapper actionMapper,
                                  AddressMapper addressMapper,
                                  ProductMapper productMapper,
                                  ProductSkuMapper skuMapper,
                                  MerchantMapper merchantMapper,
                                  OrderMapper orderMapper,
                                  OrderService orderService,
                                  ChatService chatService,
                                  AgentGateway agentGateway,
                                  AgentToolTicketService ticketService,
                                  StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  AgentActionProperties properties) {
        this.actionMapper = actionMapper;
        this.addressMapper = addressMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.merchantMapper = merchantMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.chatService = chatService;
        this.agentGateway = agentGateway;
        this.ticketService = ticketService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // ── 准备 ──

    @Override
    public AgentActionView prepareOrder(long userId, String sessionId,
                                        AgentOrderDraftRequest request) {
        requireEnabled();
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要购买的商品");
        }
        if (request.items().size() > 20) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次最多准备 20 行商品");
        }
        Address address = resolveAddress(userId, request.addressId());

        // 同一 SKU 的重复行先合并，避免用拆行绕过库存校验
        Map<Long, Integer> quantityBySku = new LinkedHashMap<>();
        Map<Long, Long> productBySku = new LinkedHashMap<>();
        for (AgentOrderDraftRequest.Item item : request.items()) {
            if (item == null || item.productId() == null || item.skuId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品与规格不能为空");
            }
            int quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity < 1 || quantity > 99) {
                throw new BizException(ResultCode.BAD_REQUEST, "购买数量需在 1–99 之间");
            }
            quantityBySku.merge(item.skuId(), quantity, Integer::sum);
            productBySku.put(item.skuId(), item.productId());
        }

        Map<Long, Product> products = productMapper.selectByIds(
                        productBySku.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSku> skus = skuMapper.selectByIds(
                        quantityBySku.keySet().stream().toList()).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityBySku.entrySet()) {
            long skuId = entry.getKey();
            int quantity = entry.getValue();
            long productId = productBySku.get(skuId);
            Product product = products.get(productId);
            ProductSku sku = skus.get(skuId);
            if (product == null || sku == null || !productIdEquals(product, sku)) {
                throw new BizException(ResultCode.NOT_FOUND, "商品或规格不存在");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品已下架：" + product.getName());
            }
            if (sku.getStatus() != null && sku.getStatus() != 1) {
                throw new BizException(ResultCode.BAD_REQUEST, "该规格已停售：" + product.getName());
            }
            if (sku.getStock() == null || sku.getStock() < quantity) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "库存不足：" + product.getName() + specSuffix(sku));
            }
            BigDecimal unitPrice = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
            lines.add(new Line(product, sku, quantity, unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(quantity))));
        }

        Map<Long, List<Line>> groups = new LinkedHashMap<>();
        for (Line line : lines) {
            groups.computeIfAbsent(line.product().getMerchantId(), key -> new ArrayList<>()).add(line);
        }
        Map<Long, Merchant> merchants = merchantMapper.selectByIds(
                        groups.keySet().stream().toList()).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> groupViews = new ArrayList<>();
        for (Map.Entry<Long, List<Line>> entry : groups.entrySet()) {
            BigDecimal subtotal = entry.getValue().stream().map(Line::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(subtotal);
            Merchant merchant = merchants.get(entry.getKey());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("merchantId", entry.getKey());
            group.put("merchantName", merchant == null ? "" : nullToEmpty(merchant.getName()));
            group.put("items", entry.getValue().stream().map(this::lineView).toList());
            group.put("subtotal", money(subtotal));
            group.put("discount", "0.00");
            group.put("total", money(subtotal));
            groupViews.add(group);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> addressView = new LinkedHashMap<>();
        addressView.put("id", address.getId());
        addressView.put("receiver", nullToEmpty(address.getReceiver()));
        addressView.put("phone", nullToEmpty(address.getPhone()));
        addressView.put("address", fullAddress(address));
        summary.put("address", addressView);
        summary.put("groups", groupViews);
        summary.put("itemCount", lines.stream().mapToInt(Line::quantity).sum());
        summary.put("originalAmount", money(total));
        summary.put("discountAmount", "0.00");
        summary.put("totalAmount", money(total));
        summary.put("note", "确认后生成待支付订单，支付请在订单详情页完成；智能下单暂不支持优惠券。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("addressId", address.getId());
        payload.put("items", lines.stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.product().getId());
            item.put("skuId", line.sku().getId());
            item.put("quantity", line.quantity());
            return item;
        }).toList());

        AgentAction action = newAction(userId, sessionId, AgentActionType.CREATE_ORDER,
                payload, summary, null);
        String token = orderService.issueToken(userId);
        redis.opsForValue().set(TOKEN_PREFIX + action.getActionNo(), token,
                Duration.ofSeconds(properties.expirySeconds()));
        actionMapper.insert(action);
        return toView(action);
    }

    @Override
    public AgentActionView prepareRefund(long userId, String sessionId,
                                         AgentRefundDraftRequest request) {
        requireEnabled();
        if (request == null || !StringUtils.hasText(request.orderNo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定要退款的订单号");
        }
        String orderNo = request.orderNo().trim();
        List<OrderView> found = orderService.findForAgent(userId, orderNo, null, 1);
        if (found.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderView view = found.get(0);
        Order order = view.order();
        OrderStatus status = parseStatus(order.getStatus());
        if (!status.refundable()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "当前订单状态（" + view.statusLabel() + "）不可申请退款");
        }

        // 幂等：同一订单已有未过期的待确认退款草稿时，直接返回那一张卡
        AgentAction existing = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getType, AgentActionType.APPLY_REFUND.name())
                .eq(AgentAction::getTargetId, order.getId())
                .eq(AgentAction::getStatus, AgentActionStatus.PENDING_CONFIRM.name())
                .orderByDesc(AgentAction::getId)
                .last("LIMIT 1"));
        if (existing != null && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
            return toView(existing);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderNo", order.getOrderNo());
        summary.put("merchantName", nullToEmpty(order.getMerchantName()));
        summary.put("status", order.getStatus());
        summary.put("statusLabel", view.statusLabel());
        summary.put("totalAmount", money(order.getTotalAmount()));
        summary.put("items", view.items() == null ? List.of()
                : view.items().stream().map(this::itemView).toList());
        summary.put("note", "确认后提交退款申请，等待商家处理；库存与优惠券在商家同意退款后回滚。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());

        AgentAction action = newAction(userId, sessionId, AgentActionType.APPLY_REFUND,
                payload, summary, order.getId());
        actionMapper.insert(action);
        return toView(action);
    }

    @Override
    public AgentActionView prepareUrgeShipment(long userId, String sessionId,
                                               AgentUrgeShipmentRequest request) {
        requireEnabled();
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
        OrderStatus status = parseStatus(order.getStatus());
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
                return toView(latest);
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
        summary.put("merchantName", nullToEmpty(order.getMerchantName()));
        summary.put("status", order.getStatus());
        summary.put("statusLabel", view.statusLabel());
        summary.put("totalAmount", money(order.getTotalAmount()));
        summary.put("itemCount", items.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum());
        summary.put("items", items.stream().map(this::itemView).toList());
        summary.put("note", "确认后买小购会以平台客服身份给商家发送一条催发货消息；"
                + "平台自营店由平台客服接收。");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());

        AgentAction action = newAction(userId, sessionId, AgentActionType.URGE_SHIPMENT,
                payload, summary, order.getId());
        actionMapper.insert(action);
        return toView(action);
    }

    // ── 确认 / 取消 / 列表 ──

    @Override
    public AgentActionView confirm(long userId, String actionNo) {
        requireEnabled();
        AgentAction action = requireOwned(userId, actionNo);
        if (AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())
                && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            actionMapper.casStatus(actionNo, userId,
                    AgentActionStatus.PENDING_CONFIRM.name(), AgentActionStatus.EXPIRED.name());
            action.setStatus(AgentActionStatus.EXPIRED.name());
            return toView(action);
        }
        if (AgentActionStatus.EXECUTING.name().equals(action.getStatus())
                && action.getUpdatedAt() != null
                && action.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            // 进程在 CAS 之后、执行完成之前崩了：留一个可恢复的失败态，避免永久卡在 EXECUTING
            action.setStatus(AgentActionStatus.FAILED.name());
            action.setErrorMessage("执行中断，请重新发起");
            action.setExecutedAt(LocalDateTime.now());
            actionMapper.updateById(action);
            return toView(action);
        }
        if (!AgentActionStatus.PENDING_CONFIRM.name().equals(action.getStatus())) {
            // 已确认 / 已失败 / 已取消 / 已过期：幂等返回当前状态，不重复执行
            return toView(action);
        }

        int started = actionMapper.startExecution(actionNo, userId);
        if (started == 0) {
            return toView(requireOwned(userId, actionNo));
        }
        action.setStatus(AgentActionStatus.EXECUTING.name());
        if (AgentActionType.URGE_SHIPMENT.name().equals(action.getType())) {
            // 催发货不在这里直接发：触发 agent 图，由 agent 调用发送工具回调 Java
            return confirmUrgeShipment(userId, action);
        }
        try {
            Object result = switch (AgentActionType.valueOf(action.getType())) {
                case CREATE_ORDER -> executeOrder(userId, action);
                case APPLY_REFUND -> executeRefund(userId, action);
                case URGE_SHIPMENT -> throw new BizException(
                        ResultCode.INTERNAL_ERROR, "催发货由 agent 工具执行");
            };
            action.setStatus(AgentActionStatus.SUCCEEDED.name());
            action.setResultJson(json(result));
            action.setErrorMessage("");
            action.setExecutedAt(LocalDateTime.now());
            actionMapper.updateById(action);
            return toView(action);
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
        requireEnabled();
        requireOwned(userId, actionNo);
        actionMapper.casStatus(actionNo, userId,
                AgentActionStatus.PENDING_CONFIRM.name(), AgentActionStatus.CANCELLED.name());
        return toView(requireOwned(userId, actionNo));
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
        return toViews(actions);
    }

    // ── 执行 ──

    private Object executeOrder(long userId, AgentAction action) {
        String token = redis.opsForValue().getAndDelete(TOKEN_PREFIX + action.getActionNo());
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.BAD_REQUEST, "草稿已过期，请重新生成");
        }
        JsonNode payload = readTree(action.getPayloadJson());
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
            item.put("totalAmount", money(view.order().getTotalAmount()));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders);
        result.put("note", "订单已生成，请到订单详情页完成支付。");
        return result;
    }

    private Object executeRefund(long userId, AgentAction action) {
        JsonNode payload = readTree(action.getPayloadJson());
        long orderId = payload.path("orderId").asLong();
        OrderView view = orderService.applyRefund(userId, orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", view.order().getId());
        result.put("orderNo", view.order().getOrderNo());
        result.put("status", view.order().getStatus());
        result.put("statusLabel", view.statusLabel());
        result.put("note", "退款申请已提交，等待商家处理。");
        return result;
    }

    @Override
    public AgentActionView executeUrgeShipment(long userId, String sessionId, String actionNo) {
        requireEnabled();
        AgentAction action = requireOwned(userId, actionNo);
        if (!AgentActionType.URGE_SHIPMENT.name().equals(action.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "动作类型不匹配");
        }
        if (StringUtils.hasText(sessionId) && StringUtils.hasText(action.getSessionId())
                && !sessionId.equals(action.getSessionId())) {
            throw new BizException(ResultCode.NOT_FOUND, "动作不存在");
        }
        if (AgentActionStatus.SUCCEEDED.name().equals(action.getStatus())) {
            return toView(action);
        }
        if (!AgentActionStatus.EXECUTING.name().equals(action.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "动作不在执行状态");
        }

        String lockKey = URGE_SEND_LOCK_PREFIX + actionNo;
        Boolean locked = redis.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            AgentAction fresh = requireOwned(userId, actionNo);
            if (AgentActionStatus.SUCCEEDED.name().equals(fresh.getStatus())) {
                return toView(fresh);
            }
            throw new BizException(ResultCode.BAD_REQUEST, "催发货正在执行，请稍后");
        }
        try {
            Map<String, Object> result = sendUrgeMessage(userId, action);
            if (actionMapper.finishExecution(actionNo, userId, json(result)) == 0) {
                AgentAction fresh = requireOwned(userId, actionNo);
                if (AgentActionStatus.SUCCEEDED.name().equals(fresh.getStatus())) {
                    return toView(fresh);
                }
                throw new BizException(ResultCode.BAD_REQUEST, "动作状态已变化，请刷新后重试");
            }
            return toView(requireOwned(userId, actionNo));
        } catch (BizException e) {
            actionMapper.failExecution(actionNo, userId, truncate(e.getMessage(), 255));
            throw e;
        } catch (Exception e) {
            log.error("催发货执行失败: {}", actionNo, e);
            actionMapper.failExecution(actionNo, userId, "催发货执行失败，请稍后重试");
            throw new BizException(ResultCode.INTERNAL_ERROR, "催发货执行失败，请稍后重试");
        } finally {
            redis.delete(lockKey);
        }
    }

    private Map<String, Object> sendUrgeMessage(long userId, AgentAction action) {
        JsonNode payload = readTree(action.getPayloadJson());
        long orderId = payload.path("orderId").asLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !userIdEquals(userId, order.getUserId())) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        OrderStatus status = parseStatus(order.getStatus());
        if (order.getShippedAt() != null || status != OrderStatus.PAID) {
            String label = order.getShippedAt() != null
                    ? OrderStatus.SHIPPED.label() : status.label();
            throw new BizException(ResultCode.BAD_REQUEST,
                    "当前订单状态（" + label + "）不可催发货");
        }
        if (order.getMerchantId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单缺少店铺信息，暂时无法催发货");
        }
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        String orderNo = nullToEmpty(order.getOrderNo());
        String content = "【催发货】订单 " + orderNo
                + " 已支付，当前待发货。买家希望尽快发货，麻烦您尽快安排；"
                + "给您带来不便，敬请谅解。—— 买小购";
        ChatMessageView message = chatService.sendAgentText(
                userId, order.getMerchantId(), content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", orderNo);
        result.put("merchantName", merchant == null ? "" : nullToEmpty(merchant.getName()));
        result.put("status", order.getStatus());
        result.put("statusLabel", status.label());
        result.put("messageId", message.id());
        result.put("note", "催发货消息已发送给商家，请留意商家回复。");
        return result;
    }

    /**
     * 确认后不直接发消息：发 gRPC 内部事件触发 agent 图；
     * agent 调 /actions/urge-shipment/send 回调 Java，由 {@link #executeUrgeShipment}
     * 校验并落库。这里只等 agent 结束，然后把动作的最终状态返回给浏览器。
     */
    private AgentActionView confirmUrgeShipment(long userId, AgentAction action) {
        if (!agentGateway.configured() || !StringUtils.hasText(action.getSessionId())) {
            actionMapper.failExecution(action.getActionNo(), userId,
                    "客服智能体不可用，暂时无法催发货");
            return toView(requireOwned(userId, action.getActionNo()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionNo", action.getActionNo());
        String ticket = ticketService.issue(userId, action.getSessionId());
        try {
            Iterator<Event> events = agentGateway.internalEvent(userId, action.getSessionId(),
                    "urge-shipment-confirmed", json(payload), ticket, Duration.ofSeconds(20));
            while (events.hasNext()) {
                Event event = events.next();
                if (event.hasError()) {
                    log.warn("催发货 agent 执行报错: {}", event.getError().getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("催发货 agent 调用失败: {}", action.getActionNo(), e);
            actionMapper.failExecution(action.getActionNo(), userId,
                    "催发货执行失败，请稍后重试");
        }
        AgentAction fresh = requireOwned(userId, action.getActionNo());
        if (AgentActionStatus.EXECUTING.name().equals(fresh.getStatus())) {
            actionMapper.failExecution(fresh.getActionNo(), userId,
                    "催发货执行超时，请重新发起");
            fresh = requireOwned(userId, action.getActionNo());
        }
        return toView(fresh);
    }

    // ── 辅助 ──

    private AgentAction newAction(long userId, String sessionId, AgentActionType type,
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

    private AgentAction requireOwned(long userId, String actionNo) {
        AgentAction action = actionMapper.selectOne(Wrappers.<AgentAction>lambdaQuery()
                .eq(AgentAction::getActionNo, actionNo)
                .eq(AgentAction::getUserId, userId));
        if (action == null) {
            throw new BizException(ResultCode.NOT_FOUND, "动作不存在");
        }
        return action;
    }

    private Address resolveAddress(long userId, Long addressId) {
        Address address;
        if (addressId != null) {
            address = addressMapper.selectById(addressId);
            if (address == null || !userIdEquals(userId, address.getUserId())) {
                throw new BizException(ResultCode.NOT_FOUND, "收货地址不存在");
            }
        } else {
            address = addressMapper.selectOne(Wrappers.<Address>lambdaQuery()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getIsDefault)
                    .orderByDesc(Address::getId)
                    .last("LIMIT 1"));
            if (address == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "请先在用户中心添加收货地址");
            }
        }
        return address;
    }

    private void markFailed(AgentAction action, String message) {
        action.setStatus(AgentActionStatus.FAILED.name());
        action.setErrorMessage(truncate(message, 255));
        action.setExecutedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    private List<AgentActionView> toViews(List<AgentAction> actions) {
        Set<Long> orderIds = new HashSet<>();
        for (AgentAction action : actions) {
            collectOrderIds(action, orderIds);
        }
        Map<Long, Order> currentOrders = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectByIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return actions.stream().map(action -> toView(action, currentOrders)).toList();
    }

    private void collectOrderIds(AgentAction action, Set<Long> orderIds) {
        if (!AgentActionStatus.SUCCEEDED.name().equals(action.getStatus())) {
            return;
        }
        Object result = readJson(action.getResultJson());
        if (!(result instanceof Map<?, ?> map)) {
            return;
        }
        if (AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
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
        } else if (AgentActionType.APPLY_REFUND.name().equals(action.getType())
                || AgentActionType.URGE_SHIPMENT.name().equals(action.getType())) {
            Long id = toLong(map.get("orderId"));
            if (id != null) {
                orderIds.add(id);
            }
        }
    }

    private AgentActionView toView(AgentAction action) {
        return toView(action, Map.of());
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

    /** 动作结果是执行时的快照；读取列表时用订单当前状态覆盖，支付 / 发货后卡片才不会停在旧状态。 */
    private void enrichResult(AgentAction action, Object result, Map<Long, Order> currentOrders) {
        if (!(result instanceof Map<?, ?> raw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        if (AgentActionType.CREATE_ORDER.name().equals(action.getType())) {
            if (map.get("orders") instanceof List<?> orders) {
                for (Object item : orders) {
                    if (item instanceof Map<?, ?> rawOrder) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> order = (Map<String, Object>) rawOrder;
                        applyOrderStatus(order, currentOrders.get(toLong(order.get("id"))));
                    }
                }
            }
        } else if (AgentActionType.APPLY_REFUND.name().equals(action.getType())
                || AgentActionType.URGE_SHIPMENT.name().equals(action.getType())) {
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

    private Map<String, Object> lineView(Line line) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("productId", line.product().getId());
        view.put("productName", nullToEmpty(line.product().getName()));
        view.put("specText", nullToEmpty(line.sku().getSpecText()));
        view.put("quantity", line.quantity());
        view.put("unitPrice", money(line.unitPrice()));
        view.put("subtotal", money(line.subtotal()));
        view.put("image", StringUtils.hasText(line.sku().getImage())
                ? line.sku().getImage() : nullToEmpty(line.product().getMainImage()));
        return view;
    }

    private Map<String, Object> itemView(OrderItem item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("productName", nullToEmpty(item.getProductName()));
        view.put("specText", nullToEmpty(item.getSpecText()));
        view.put("quantity", item.getQuantity());
        view.put("unitPrice", money(item.getUnitPrice()));
        view.put("subtotal", money(item.getSubtotal()));
        view.put("image", nullToEmpty(item.getProductImage()));
        return view;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("动作快照损坏: {}", json, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "动作快照损坏，请重新生成");
        }
    }

    private Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "动作快照生成失败");
        }
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单状态异常");
        }
    }

    private String actionNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String specSuffix(ProductSku sku) {
        return StringUtils.hasText(sku.getSpecText()) ? "（" + sku.getSpecText() + "）" : "";
    }

    private String fullAddress(Address address) {
        return nullToEmpty(address.getProvince()) + nullToEmpty(address.getCity())
                + nullToEmpty(address.getDistrict()) + nullToEmpty(address.getDetail());
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean productIdEquals(Product product, ProductSku sku) {
        return sku.getProductId() != null && Objects.equals(product.getId(), sku.getProductId());
    }

    private boolean userIdEquals(long userId, Long ownerId) {
        return ownerId != null && ownerId == userId;
    }

    private void requireEnabled() {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "智能下单 / 退款暂未开放");
        }
    }

    private record Line(Product product, ProductSku sku, int quantity,
                        BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
