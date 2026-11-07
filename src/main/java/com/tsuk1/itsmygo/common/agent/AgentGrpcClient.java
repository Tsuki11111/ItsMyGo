package com.tsuk1.itsmygo.common.agent;

import agent.v1.AgentServiceGrpc;
import agent.v1.ConverseRequest;
import agent.v1.Event;
import agent.v1.FormResult;
import agent.v1.HistoryMessage;
import agent.v1.HistoryRequest;
import agent.v1.ListSessionsRequest;
import agent.v1.SessionInfo;
import agent.v1.StopRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

/** 买小购 gRPC 客户端：内网明文、metadata 注入身份、不做自动重试。 */
@Slf4j
@Component
public class AgentGrpcClient implements AgentGateway {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", ASCII_STRING_MARSHALLER);
    /** 本轮工具票据：agent 回调 Java 内部工具接口时带回，Java 据此解析 userId */
    private static final Metadata.Key<String> TOOL_TICKET =
            Metadata.Key.of("x-agent-tool-ticket", ASCII_STRING_MARSHALLER);
    /** Java 动作完成后的内部事件；浏览器无法设置，只有持有 API key 的 Java 能发 */
    private static final Metadata.Key<String> INTERNAL_EVENT =
            Metadata.Key.of("x-agent-internal-event", ASCII_STRING_MARSHALLER);

    private static final Duration SHORT_RPC_DEADLINE = Duration.ofSeconds(15);

    private final AgentProperties properties;
    private final ManagedChannel channel;

    public AgentGrpcClient(AgentProperties properties) {
        this.properties = properties;
        this.channel = NettyChannelBuilder.forTarget(properties.target())
                .usePlaintext()
                // Python grpc 默认要求 ping 间隔 ≥300s，别设太小
                .keepAliveTime(300, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .build();
        log.info("买小购 gRPC 客户端目标: {}", properties.target());
    }

    @Override
    public boolean configured() {
        return StringUtils.hasText(properties.key());
    }

    @Override
    public Iterator<Event> converse(long userId, String sessionId, String message,
                                    FormResult formResult, Duration deadline,
                                    String toolTicket, boolean enableWebSearch, boolean stream) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId, toolTicket)
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        ConverseRequest.Builder request = ConverseRequest.newBuilder()
                .setSessionId(sessionId == null ? "" : sessionId)
                .setMessage(message)
                // 客服设置：显式下发（默认值就是旧行为：联网开、流式关），
                // optional 字段的 has 语义只用于兼容老客户端，这里不需要区分「未设置」
                .setEnableWebSearch(enableWebSearch)
                .setStream(stream);
        // 旧客户端不带表单回传：保持不设置（proto3 message 字段默认即空）
        if (formResult != null) {
            request.setFormResult(formResult);
        }
        return stub.converse(request.build());
    }

    @Override
    public Iterator<Event> internalEvent(long userId, String sessionId, String event,
                                         String payload, String toolTicket, Duration deadline) {
        Metadata metadata = metadata(userId, toolTicket);
        metadata.put(INTERNAL_EVENT, event);
        AgentServiceGrpc.AgentServiceBlockingStub stub =
                AgentServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        return stub.converse(ConverseRequest.newBuilder()
                .setSessionId(sessionId == null ? "" : sessionId)
                .setMessage(payload == null ? "" : payload)
                .build());
    }

    @Override
    public boolean stop(long userId, String sessionId, String runId) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.stop(StopRequest.newBuilder()
                .setSessionId(sessionId)
                .setRunId(runId)
                .build()).getStopped();
    }

    @Override
    public List<HistoryMessage> history(long userId, String sessionId, int limit) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.getHistory(HistoryRequest.newBuilder()
                .setSessionId(sessionId)
                .setLimit(limit)
                .build()).getMessagesList();
    }

    @Override
    public List<SessionInfo> sessions(long userId, int limit) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.listSessions(ListSessionsRequest.newBuilder()
                .setLimit(limit)
                .build()).getSessionsList();
    }

    private AgentServiceGrpc.AgentServiceBlockingStub stub(long userId) {
        return stub(userId, null);
    }

    private AgentServiceGrpc.AgentServiceBlockingStub stub(long userId, String toolTicket) {
        return AgentServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(
                        metadata(userId, toolTicket)));
    }

    private Metadata metadata(long userId, String toolTicket) {
        Metadata metadata = new Metadata();
        // 客户端同名 metadata 不会被转发：这是 Java 新构造的 Metadata，由 Java 独占
        metadata.put(AUTHORIZATION, "Bearer " + properties.key());
        metadata.put(USER_ID, String.valueOf(userId));
        if (StringUtils.hasText(toolTicket)) {
            metadata.put(TOOL_TICKET, toolTicket);
        }
        return metadata;
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdownNow();
    }
}
