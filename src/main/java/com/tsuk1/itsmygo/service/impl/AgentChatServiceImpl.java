package com.tsuk1.itsmygo.service.impl;

import agent.v1.Event;
import agent.v1.FormField;
import agent.v1.FormOption;
import agent.v1.FormRequest;
import agent.v1.FormResult;
import agent.v1.HistoryMessage;
import agent.v1.SessionInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentEventMapper;
import com.tsuk1.itsmygo.common.agent.AgentFormGuard;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.common.agent.AgentProperties;
import com.tsuk1.itsmygo.common.agent.AgentSseSink;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AfterSaleReasonView;
import com.tsuk1.itsmygo.dto.agent.AgentConverseRequest;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryMessage;
import com.tsuk1.itsmygo.dto.agent.AgentHistoryView;
import com.tsuk1.itsmygo.dto.agent.AgentPreferenceView;
import com.tsuk1.itsmygo.dto.agent.AgentSessionView;
import com.tsuk1.itsmygo.dto.agent.AgentStopRequest;
import com.tsuk1.itsmygo.entity.AgentSession;
import com.tsuk1.itsmygo.mapper.AgentSessionMapper;
import com.tsuk1.itsmygo.common.identity.CurrentUser;
import com.tsuk1.itsmygo.service.AfterSaleService;
import com.tsuk1.itsmygo.service.AgentChatService;
import com.tsuk1.itsmygo.service.AgentPreferenceService;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private static final int MAX_HISTORY = 50;
    private static final String FORM_TYPE_REFUND_REASON = "refund_reason";
    private static final String FORM_FIELD_REASON = "reason";

    private final AgentGateway gateway;
    private final AgentProperties properties;
    private final AgentSessionMapper sessionMapper;
    private final AgentToolTicketService toolTicketService;
    private final AgentFormGuard formGuard;
    private final AfterSaleService afterSaleService;
    private final AgentPreferenceService preferenceService;
    private final ExecutorService agentExecutor;
    private final ScheduledExecutorService auxExecutor;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> activeStreams = new ConcurrentHashMap<>();

    public AgentChatServiceImpl(AgentGateway gateway, AgentProperties properties,
                                AgentSessionMapper sessionMapper,
                                AgentToolTicketService toolTicketService,
                                AgentFormGuard formGuard,
                                AfterSaleService afterSaleService,
                                AgentPreferenceService preferenceService,
                                @Qualifier("agentExecutor") ExecutorService agentExecutor,
                                @Qualifier("agentAuxExecutor") ScheduledExecutorService auxExecutor,
                                ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.toolTicketService = toolTicketService;
        this.formGuard = formGuard;
        this.afterSaleService = afterSaleService;
        this.preferenceService = preferenceService;
        this.agentExecutor = agentExecutor;
        this.auxExecutor = auxExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter converse(CurrentUser user, AgentConverseRequest request) {
        requireConfigured();
        // 客服设置：在请求线程读（无行时是默认值 0/1/0），随本轮 gRPC Converse 一起下发
        AgentPreferenceView preference = preferenceService.get(user.id());
        String message = request.message().trim();
        String sessionId;
        if (StringUtils.hasText(request.sessionId())) {
            sessionId = request.sessionId().trim();
            requireOwned(sessionId, user.id());
            sessionMapper.touch(sessionId, user.id());
        } else {
            // Java 先生成并记录归属，再调 Converse；不能等 SessionStart 回来才写，
            // 否则异步线程写库有竞态，客户端也可能拿到未登记的 id。
            sessionId = UUID.randomUUID().toString().replace("-", "");
            sessionMapper.insertSession(user.id(), sessionId);
        }

        if (activeStreams.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "该会话正在回复中，请稍候");
        }

        // 本轮工具票据：agent 的 order-service 工具回调 /internal/agent/** 时带回。
        // 只放 metadata，不下发浏览器，也不进模型上下文。
        String toolTicket = toolTicketService.issue(user.id(), sessionId);

        SseEmitter emitter = new SseEmitter(properties.deadlineSeconds() * 1000L + 30_000);
        AgentSseSink sink = new AgentSseSink(emitter, objectMapper, auxExecutor);
        Context.CancellableContext context = Context.current().withCancellation();
        AtomicReference<String> runIdRef = new AtomicReference<>();
        AtomicBoolean finalSeen = new AtomicBoolean(false);
        AtomicBoolean errorSeen = new AtomicBoolean(false);
        AtomicBoolean released = new AtomicBoolean(false);

        String fixedSessionId = sessionId;
        Runnable cleanup = () -> {
            context.cancel(null);
            sink.close();
            if (released.compareAndSet(false, true)) {
                activeStreams.remove(fixedSessionId);
            }
            String runId = runIdRef.get();
            if (runId != null && !finalSeen.get() && !errorSeen.get()) {
                // gRPC context cancel 只断流；Python worker 阻塞在队列上，必须用 runId 停
                auxExecutor.execute(() -> {
                    try {
                        gateway.stop(user.id(), fixedSessionId, runId);
                    } catch (Exception e) {
                        log.debug("客服 Stop 失败（忽略）: {}", e.getMessage());
                    }
                });
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        sink.start();
        try {
            FormResult formResult = toFormResult(request.formResult());
            agentExecutor.execute(() -> stream(user, fixedSessionId, message, formResult, toolTicket,
                    preference.webSearchEnabled(), preference.streamEnabled(),
                    sink, context, runIdRef, finalSeen, errorSeen, cleanup));
        } catch (RejectedExecutionException e) {
            cleanup.run();
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "客服繁忙，请稍后再试");
        }
        return emitter;
    }

    /** 前端表单回传 → gRPC 消息；空/缺失一律视为普通文本对话（兼容老客户端）。 */
    private FormResult toFormResult(AgentConverseRequest.FormResult source) {
        if (source == null || !StringUtils.hasText(source.formId())) {
            return null;
        }
        FormResult.Builder builder = FormResult.newBuilder().setFormId(source.formId().trim());
        if (source.values() != null) {
            source.values().forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    builder.putValues(name, value);
                }
            });
        }
        return builder.build();
    }

    /**
     * 退款原因校验：回传值必须落在售后原因字典的展示名里。
     * 表单回传只有 {@code form_id + values}、不带 formType，所以按「有 reason 字段就校验」处理；
     * 其它表单不受影响。非法返回 null（调用方发 FORM_INVALID）。
     */
    private FormResult normalizeRefundReason(FormResult formResult) {
        Map<String, String> values = formResult.getValuesMap();
        if (!values.containsKey(FORM_FIELD_REASON)) {
            return formResult;
        }
        String raw = values.get(FORM_FIELD_REASON);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String reason = raw.trim();
        if (!refundReasonLabels().contains(reason)) {
            return null;
        }
        return reason.equals(raw) ? formResult
                : formResult.toBuilder().putValues(FORM_FIELD_REASON, reason).build();
    }

    /** 售后原因字典（启用中的）展示名集合：表单选项与提交校验共用这一份口径。 */
    private Set<String> refundReasonLabels() {
        return afterSaleService.reasons().stream()
                .map(AfterSaleReasonView::label)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * refund_reason 表单的 select 选项由 Java 补全：rag_helper 只下发空 options，
     * 原因字典保持售后模块单一口径。选项 value 直接用中文展示名 ——
     * 回传值最终会作为订单的退款原因文本（{@code orders.refund_reason}），
     * 用稳定编码会让用户看到 {@code QUALITY} 之类的内部值。
     */
    private Event fillRefundReasonOptions(Event event) {
        List<FormRequest> forms = event.getFinal().getFormsList();
        if (forms.isEmpty()) {
            return event;
        }
        boolean changed = false;
        List<FormRequest> filled = new ArrayList<>(forms.size());
        for (FormRequest form : forms) {
            FormRequest next = fillRefundReasonOptions(form);
            changed |= next != form;
            filled.add(next);
        }
        if (!changed) {
            return event;
        }
        return event.toBuilder().setFinal(event.getFinal().toBuilder()
                .clearForms().addAllForms(filled).build()).build();
    }

    private FormRequest fillRefundReasonOptions(FormRequest form) {
        if (!FORM_TYPE_REFUND_REASON.equals(form.getFormType())) {
            return form;
        }
        List<FormOption> options = null;
        boolean changed = false;
        List<FormField> fields = new ArrayList<>(form.getFieldsCount());
        for (FormField field : form.getFieldsList()) {
            if (!FORM_FIELD_REASON.equals(field.getName()) || field.getOptionsCount() > 0) {
                fields.add(field);
                continue;
            }
            if (options == null) {
                options = afterSaleService.reasons().stream()
                        .filter(reason -> StringUtils.hasText(reason.label()))
                        .map(reason -> FormOption.newBuilder()
                                .setValue(reason.label())
                                .setLabel(reason.label())
                                .build())
                        .toList();
            }
            if (options.isEmpty()) {
                log.warn("售后原因字典为空，退款原因表单保持空选项: formId={}", form.getFormId());
                fields.add(field);
                continue;
            }
            fields.add(field.toBuilder().addAllOptions(options).build());
            changed = true;
        }
        if (!changed) {
            return form;
        }
        return form.toBuilder().clearFields().addAllFields(fields).build();
    }

    private void stream(CurrentUser user, String sessionId, String message, FormResult formResult,
                        String toolTicket, boolean enableWebSearch, boolean streamEnabled,
                        AgentSseSink sink, Context.CancellableContext context,
                        AtomicReference<String> runIdRef,
                        AtomicBoolean finalSeen, AtomicBoolean errorSeen, Runnable cleanup) {
        try {
            FormResult forwardedForm = formResult;
            if (forwardedForm != null) {
                // 先校验后消费：非法提交不能白白烧掉一张一次性表单
                forwardedForm = normalizeRefundReason(forwardedForm);
                if (forwardedForm == null) {
                    errorSeen.set(true);
                    sink.send("error", AgentEventMapper.error("FORM_INVALID",
                            "退款原因不在可选范围内，请重新选择后提交", false));
                    sink.send("done", AgentEventMapper.done("error"));
                    return;
                }
                if (!formGuard.tryConsume(user.id(), forwardedForm.getFormId())) {
                    // SETNX 失败：不调 agent，直接告诉前端这张表单已经提交过
                    errorSeen.set(true);
                    sink.send("error", AgentEventMapper.error("FORM_ALREADY_SUBMITTED",
                            "这张表单已经提交过了，请重新发起一次", false));
                    sink.send("done", AgentEventMapper.done("error"));
                    return;
                }
            }
            FormResult effectiveForm = forwardedForm;
            Iterator<Event> events = context.call(() -> gateway.converse(
                    user.id(), sessionId, message, effectiveForm,
                    Duration.ofSeconds(properties.deadlineSeconds()), toolTicket,
                    enableWebSearch, streamEnabled));

            while (!sink.isClosed() && events.hasNext()) {
                Event event = events.next();
                Event.KindCase kind = event.getKindCase();
                if (kind == Event.KindCase.START) {
                    runIdRef.set(event.getStart().getRunId());
                } else if (kind == Event.KindCase.FINAL) {
                    finalSeen.set(true);
                    // 退款原因选项由 Java 用售后原因字典补全（agent 只下发空选项）
                    event = fillRefundReasonOptions(event);
                } else if (kind == Event.KindCase.ERROR) {
                    errorSeen.set(true);
                    log.warn("买小购返回错误事件: {}", event.getError().getMessage());
                }
                AgentEventMapper.Mapped mapped = AgentEventMapper.map(event);
                if (mapped != null) {
                    sink.send(mapped.event(), mapped.data());
                }
            }

            if (!sink.isClosed()) {
                String reason = finalSeen.get() ? "final" : errorSeen.get() ? "error" : "stopped";
                sink.send("done", AgentEventMapper.done(reason));
            }
        } catch (StatusRuntimeException e) {
            if (!sink.isClosed()) {
                log.warn("买小购 gRPC 流失败: {} {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                sink.send("error", grpcError(e));
                sink.send("done", AgentEventMapper.done("error"));
            }
        } catch (Exception e) {
            if (!sink.isClosed()) {
                log.error("买小购流处理异常", e);
                sink.send("error", AgentEventMapper.error(
                        "AGENT_ERROR", "客服处理失败，请稍后重试", true));
                sink.send("done", AgentEventMapper.done("error"));
            }
        } finally {
            cleanup.run();
        }
    }

    @Override
    public boolean stop(CurrentUser user, AgentStopRequest request) {
        requireConfigured();
        requireOwned(request.sessionId(), user.id());
        try {
            boolean stopped = gateway.stop(user.id(), request.sessionId(), request.runId());
            sessionMapper.touch(request.sessionId(), user.id());
            return stopped;
        } catch (StatusRuntimeException e) {
            log.warn("停止买小购失败: {} {}", e.getStatus().getCode(), e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "停止失败，请稍后再试");
        }
    }

    @Override
    public AgentHistoryView history(CurrentUser user, String sessionId, int limit) {
        requireConfigured();
        requireOwned(sessionId, user.id());
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        try {
            List<HistoryMessage> raw = gateway.history(user.id(), sessionId, safeLimit);
            List<AgentHistoryMessage> messages = raw.stream()
                    .map(item -> new AgentHistoryMessage(
                            item.getRole(),
                            item.getText(),
                            item.getImagesList().stream()
                                    .map(image -> new AgentHistoryMessage.AgentImage(
                                            image.getUrl(), image.getCaption()))
                                    .toList(),
                            item.getTs(),
                            item.getTraceList().stream()
                                    .map(trace -> new AgentHistoryMessage.AgentTrace(
                                            trace.getKind(), trace.getAgent(), trace.getDetail()))
                                    .toList(),
                            item.getProductsList().stream()
                                    .map(product -> new AgentHistoryMessage.AgentProductCard(
                                            product.getProductId(), product.getName(),
                                            product.getImage(), product.getPrice(),
                                            product.getStock(), product.getSales(),
                                            product.getRating(), product.getMerchantName(),
                                            product.getSpecText(), product.getCategoryName()))
                                    .toList(),
                            item.getFormsList().stream()
                                    // 与实时流一致：rag_helper 存的 options 是空的，
                                    // 退款原因字典由 Java 补全，历史回放同样要补
                                    .map(this::fillRefundReasonOptions)
                                    .map(form -> new AgentHistoryMessage.AgentFormRequest(
                                            form.getFormId(), form.getFormType(), form.getTitle(),
                                            form.getDescription(),
                                            form.getFieldsList().stream()
                                                    .map(field -> new AgentHistoryMessage.AgentFormField(
                                                            field.getName(), field.getLabel(),
                                                            field.getType(), field.getRequired(),
                                                            field.getPlaceholder(),
                                                            field.getDefaultValue(),
                                                            field.getOptionsList().stream()
                                                                    .map(option ->
                                                                            new AgentHistoryMessage.AgentFormOption(
                                                                                    option.getValue(),
                                                                                    option.getLabel()))
                                                                    .toList()))
                                                    .toList(),
                                            form.getSubmitLabel(), form.getExpiresInSeconds(),
                                            new LinkedHashMap<>(form.getMetaMap())))
                                    .toList()))
                    .toList();
            sessionMapper.touch(sessionId, user.id());
            return new AgentHistoryView(messages);
        } catch (StatusRuntimeException e) {
            log.warn("加载买小购历史失败: {} {}", e.getStatus().getCode(),
                    e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服历史加载失败，请稍后再试");
        }
    }

    @Override
    public List<AgentSessionView> sessions(CurrentUser user, int limit) {
        requireConfigured();
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        try {
            // agent 已按 x-user-id 过滤；这里再按 Java 的归属表过滤一次，双保险
            Set<String> owned = sessionMapper.selectList(
                            Wrappers.<AgentSession>lambdaQuery()
                                    .eq(AgentSession::getUserId, user.id()))
                    .stream().map(AgentSession::getSessionId).collect(Collectors.toSet());
            return gateway.sessions(user.id(), safeLimit).stream()
                    .filter(item -> owned.contains(item.getSessionId()))
                    .map(this::toSessionView)
                    .toList();
        } catch (StatusRuntimeException e) {
            log.warn("加载买小购会话列表失败: {} {}", e.getStatus().getCode(),
                    e.getStatus().getDescription());
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服会话列表加载失败，请稍后再试");
        }
    }

    private AgentSessionView toSessionView(SessionInfo info) {
        return new AgentSessionView(info.getSessionId(), info.getTitle(),
                info.getCount(), info.getLastAt());
    }

    private void requireConfigured() {
        if (!gateway.configured()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "客服智能体未配置，请联系管理员");
        }
    }

    private void requireOwned(String sessionId, Long userId) {
        if (!StringUtils.hasText(sessionId)
                || sessionMapper.selectOwned(sessionId, userId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
    }

    private Map<String, Object> grpcError(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case UNAVAILABLE -> AgentEventMapper.error(
                    "AGENT_UNAVAILABLE", "客服智能体暂时不可用，请稍后再试", true);
            case DEADLINE_EXCEEDED -> AgentEventMapper.error(
                    "AGENT_TIMEOUT", "客服响应超时，请重试", true);
            case UNAUTHENTICATED, PERMISSION_DENIED -> AgentEventMapper.error(
                    "AGENT_AUTH_FAILED", "客服服务鉴权失败，请联系管理员", false);
            case RESOURCE_EXHAUSTED -> AgentEventMapper.error(
                    "AGENT_BUSY", "客服繁忙，请稍后再试", true);
            default -> AgentEventMapper.error(
                    "AGENT_ERROR", "客服服务异常，请稍后再试", true);
        };
    }
}
