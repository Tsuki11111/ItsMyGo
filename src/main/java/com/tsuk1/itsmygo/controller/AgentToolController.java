package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.CategoryNode;
import com.tsuk1.itsmygo.dto.OrderView;
import com.tsuk1.itsmygo.dto.agent.AgentActionView;
import com.tsuk1.itsmygo.dto.agent.AgentAddressView;
import com.tsuk1.itsmygo.dto.agent.AgentOrderDraftRequest;
import com.tsuk1.itsmygo.dto.agent.AgentOrderQueryRequest;
import com.tsuk1.itsmygo.dto.agent.AgentOrderView;
import com.tsuk1.itsmygo.dto.agent.AgentProductSearchRequest;
import com.tsuk1.itsmygo.dto.agent.AgentProductView;
import com.tsuk1.itsmygo.dto.agent.AgentRefundDraftRequest;
import com.tsuk1.itsmygo.service.AgentActionService;
import com.tsuk1.itsmygo.service.AgentCatalogService;
import com.tsuk1.itsmygo.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * agent 服务回调的内部工具接口。**只走内网**，浏览器不调用。
 *
 * <p>鉴权只认 {@code X-Agent-Ticket}：票据由 Java 在每次 Converse 时签发，
 * 经 gRPC metadata 下发给 agent。请求体里没有 userId —— 模型无法指定用户。
 */
@RestController
@RequestMapping("/internal/agent")
public class AgentToolController {

    private static final int DEFAULT_LIMIT = 5;

    private final AgentToolTicketService ticketService;
    private final OrderService orderService;
    private final AgentCatalogService catalogService;
    private final AgentActionService actionService;

    public AgentToolController(AgentToolTicketService ticketService,
                               OrderService orderService,
                               AgentCatalogService catalogService,
                               AgentActionService actionService) {
        this.ticketService = ticketService;
        this.orderService = orderService;
        this.catalogService = catalogService;
        this.actionService = actionService;
    }

    @PostMapping("/orders/query")
    public Result<List<AgentOrderView>> query(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket,
            @RequestBody(required = false) AgentOrderQueryRequest request) {
        AgentToolTicketService.Identity identity = requireIdentity(ticket);
        String orderNo = request == null ? null : request.orderNo();
        String status = request == null ? null : request.status();
        int limit = request == null || request.limit() == null ? DEFAULT_LIMIT : request.limit();
        List<OrderView> orders = orderService.findForAgent(identity.userId(), orderNo, status, limit);
        return Result.ok(orders.stream().map(AgentOrderView::from).toList());
    }

    @PostMapping("/catalog/products")
    public Result<List<AgentProductView>> searchProducts(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket,
            @RequestBody(required = false) AgentProductSearchRequest request) {
        requireIdentity(ticket);
        return Result.ok(catalogService.searchProducts(request));
    }

    @GetMapping("/catalog/all")
    public Result<List<AgentProductView>> allProducts(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket,
            @RequestParam(defaultValue = "100") int limit) {
        requireIdentity(ticket);
        return Result.ok(catalogService.listAll(limit));
    }

    @GetMapping("/catalog/categories")
    public Result<List<CategoryNode>> categories(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket) {
        requireIdentity(ticket);
        return Result.ok(catalogService.categories());
    }

    @GetMapping("/catalog/addresses")
    public Result<List<AgentAddressView>> addresses(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket) {
        AgentToolTicketService.Identity identity = requireIdentity(ticket);
        return Result.ok(catalogService.addresses(identity.userId()));
    }

    @PostMapping("/actions/order")
    public Result<AgentActionView> prepareOrder(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket,
            @RequestBody(required = false) AgentOrderDraftRequest request) {
        AgentToolTicketService.Identity identity = requireIdentity(ticket);
        return Result.ok(actionService.prepareOrder(
                identity.userId(), identity.sessionId(), request));
    }

    @PostMapping("/actions/refund")
    public Result<AgentActionView> prepareRefund(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket,
            @RequestBody(required = false) AgentRefundDraftRequest request) {
        AgentToolTicketService.Identity identity = requireIdentity(ticket);
        return Result.ok(actionService.prepareRefund(
                identity.userId(), identity.sessionId(), request));
    }

    private AgentToolTicketService.Identity requireIdentity(String ticket) {
        AgentToolTicketService.Identity identity = ticketService.resolve(ticket);
        if (identity == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "工具票据无效或已过期");
        }
        return identity;
    }
}
